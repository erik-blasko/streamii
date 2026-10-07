package com.erikblasko.tvstreamapp

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import com.erikblasko.tvstreamapp.data.model.StreamSource
import com.erikblasko.tvstreamapp.databinding.ActivityPlayerBinding
import com.erikblasko.tvstreamapp.playback.PlaybackService
import com.erikblasko.tvstreamapp.playback.isAudioOnly
import com.erikblasko.tvstreamapp.util.AppLocale
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

/**
 * Screen of the player in [PlaybackService]. TV pauses when the screen is left (or goes to
 * picture-in-picture); radio, also with a studio camera, keeps playing in the background
 * until the screen is closed with Back.
 *
 * The top bar holds the live delay, quality and sleep timer; it shows with the player controls.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_CHANNEL_ID = "channel_id"
        private const val EXTRA_SOURCES = "sources"
        private const val EXTRA_CHANNEL_NAME = "channel_name"
        private const val EXTRA_LOGO_URL = "logo_url"
        private const val EXTRA_IS_RADIO = "is_radio"
        private const val EXTRA_RESUME = "resume"
        private const val STATE_STARTED = "started"

        private val SLEEP_MINUTES = listOf(15, 30, 60, 90)

        /**
         * [sources]: the chosen one first; the others take over in this order when it fails.
         * [isRadio]: the station is radio by name; streams with sound only are treated as radio too.
         * [resume]: open the stream that is already playing instead of starting it again.
         */
        fun intent(
            context: Context,
            channelId: String,
            sources: List<StreamSource>,
            name: String,
            logoUrl: String?,
            isRadio: Boolean = false,
            resume: Boolean = false
        ): Intent = Intent(context, PlayerActivity::class.java)
            .putExtra(EXTRA_CHANNEL_ID, channelId)
            .putExtra(EXTRA_SOURCES, PlaybackService.sourcesBundle(sources))
            .putExtra(EXTRA_CHANNEL_NAME, name)
            .putExtra(EXTRA_LOGO_URL, logoUrl)
            .putExtra(EXTRA_IS_RADIO, isRadio)
            .putExtra(EXTRA_RESUME, resume)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    private lateinit var binding: ActivityPlayerBinding
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    /** Whether this screen's stream was handed to the player (it keeps playing between visits) */
    private var started = false

    /** Last finding about the stream (true = sound only), so it is saved once per change */
    private var detectedRadio: Boolean? = null

    private var liveDelay = false

    private val handler = Handler(Looper.getMainLooper())
    private val sleepTicker = object : Runnable {
        override fun run() {
            updateSleepChip()
            handler.postDelayed(this, 15_000)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TRACKS_CHANGED)) onStreamTracksKnown(player)
            updateTopBar()
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (command.customAction == PlaybackService.COMMAND_SOURCE_SWITCHED) {
                val index = args.getInt(PlaybackService.EXTRA_SOURCE_INDEX)
                val count = args.getInt(PlaybackService.EXTRA_SOURCE_COUNT)
                Toast.makeText(
                    this@PlayerActivity,
                    getString(R.string.player_source_switched, index + 1, count),
                    Toast.LENGTH_SHORT
                ).show()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            updateSleepChip()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (sources().isEmpty()) {
            finish()
            return
        }
        started = savedInstanceState?.getBoolean(STATE_STARTED) ?: intent.getBooleanExtra(EXTRA_RESUME, false)

        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                binding.topBar.visibility = if (isInPictureInPictureMode) View.GONE else visibility
            }
        )
        binding.playerView.setErrorMessageProvider { error: PlaybackException ->
            val count = controller?.sessionExtras?.getInt(PlaybackService.EXTRA_SOURCE_COUNT) ?: 1
            val message = if (count > 1) getString(R.string.player_error_all_sources, count)
            else getString(R.string.player_error)
            android.util.Pair(error.errorCode, message)
        }
        binding.liveChip.setOnClickListener { showLiveModeDialog() }
        binding.qualityChip.setOnClickListener { showQualityDialog() }
        binding.sleepChip.setOnClickListener { showSleepDialog() }

        val preferences = (application as StreamiiApp).container.preferences
        lifecycleScope.launch {
            preferences.liveDelay.collect {
                liveDelay = it
                updateTopBar()
            }
        }

        showChannelInfo()
        hideSystemUi()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_STARTED, started)
    }

    /** Opened again from the notification, maybe for another channel. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        started = intent.getBooleanExtra(EXTRA_RESUME, false)
        detectedRadio = null
        showChannelInfo()
    }

    private fun sources(): List<StreamSource> =
        PlaybackService.sourcesFrom(intent.getBundleExtra(EXTRA_SOURCES))

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture = future
        future.addListener({
            val connected = runCatching { future.get() }.getOrNull()
            // The screen may have been left before the connection was made
            if (connected != null && controllerFuture === future) onConnected(connected)
        }, ContextCompat.getMainExecutor(this))
        handler.post(sleepTicker)
    }

    private fun onConnected(controller: MediaController) {
        this.controller = controller
        binding.playerView.player = controller
        controller.addListener(playerListener)

        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID).orEmpty()
        // Coming back keeps the stream going; it only starts on the first visit or for another channel
        if (!started || controller.currentMediaItem?.mediaId != channelId) {
            // A quality picked for the previous channel does not apply to this one
            controller.trackSelectionParameters = controller.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build()
            controller.setMediaItem(
                PlaybackService.mediaItem(
                    channelId = channelId,
                    name = intent.getStringExtra(EXTRA_CHANNEL_NAME).orEmpty(),
                    logoUrl = intent.getStringExtra(EXTRA_LOGO_URL),
                    sources = sources(),
                    isRadio = intent.getBooleanExtra(EXTRA_IS_RADIO, false)
                )
            )
            controller.prepare()
            started = true
        } else if (controller.playbackState == Player.STATE_IDLE) {
            controller.prepare()
        }
        controller.play()
        onStreamTracksKnown(controller)
        updateTopBar()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(sleepTicker)
        controller?.let { controller ->
            when {
                // Closed with Back: playback ends, radio included
                isFinishing -> {
                    controller.stop()
                    controller.clearMediaItems()
                }
                isInPictureInPictureMode || playsInBackground(controller) -> {}
                else -> controller.pause()
            }
            controller.removeListener(playerListener)
        }
        binding.playerView.player = null
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    /** Radio keeps playing when the screen is left; TV pauses or goes to picture-in-picture. */
    private fun playsInBackground(player: Player): Boolean =
        intent.getBooleanExtra(EXTRA_IS_RADIO, false) || player.isAudioOnly() == true

    /**
     * Streams with sound only are radio stations: the channel list moves them to the radio
     * section (and back if a picture shows up later, unless the name says radio).
     */
    private fun onStreamTracksKnown(player: Player) {
        val isRadio = player.isAudioOnly() ?: return
        // Radio lets the screen turn off; it keeps playing
        if (isRadio) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (isRadio == detectedRadio) return
        detectedRadio = isRadio
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID)?.takeIf { it.isNotEmpty() } ?: return
        val preferences = (application as StreamiiApp).container.preferences
        lifecycleScope.launch { preferences.setDetectedRadio(channelId, isRadio) }
    }

    // --- Top bar ---

    private fun updateTopBar() {
        val player = controller
        binding.liveChip.visibility =
            if (player != null && player.isCurrentMediaItemLive) View.VISIBLE else View.GONE
        if (liveDelay) {
            binding.liveChip.text = getString(R.string.player_delayed)
            binding.liveChip.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
        } else {
            binding.liveChip.text = getString(R.string.player_live)
            binding.liveChip.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.player_live_dot, 0, 0, 0)
        }

        val qualities = player?.let { videoQualities(it) }.orEmpty()
        binding.qualityChip.visibility = if (qualities.size > 1) View.VISIBLE else View.GONE
        binding.qualityChip.text = player?.let { selectedQuality(it) }?.let { "${it}p" }
            ?: getString(R.string.player_quality_auto)

        updateSleepChip()
    }

    private fun updateSleepChip() {
        val sleepAt = controller?.sessionExtras?.getLong(PlaybackService.EXTRA_SLEEP_AT) ?: 0L
        val remainingMs = sleepAt - SystemClock.elapsedRealtime()
        binding.sleepChip.text = if (sleepAt > 0 && remainingMs > 0) {
            // Rounded up, so "1 min" shows until the end
            getString(R.string.player_sleep_remaining, ((remainingMs + 59_999) / 60_000).toInt())
        } else {
            getString(R.string.player_sleep)
        }
    }

    private fun showLiveModeDialog() {
        val options = arrayOf(getString(R.string.player_live_mode_live), getString(R.string.player_live_mode_delayed))
        dialogBuilder()
            .setTitle(R.string.player_live_mode)
            .setSingleChoiceItems(options, if (liveDelay) 1 else 0) { dialog, which ->
                val delay = which == 1
                val preferences = (application as StreamiiApp).container.preferences
                // The service follows the setting and restarts the stream at the new offset
                lifecycleScope.launch { preferences.setLiveDelay(delay) }
                dialog.dismiss()
            }
            .show()
    }

    private class Quality(val height: Int, val group: TrackGroup, val trackIndex: Int)

    /** Resolutions the stream offers, highest first. */
    private fun videoQualities(player: Player): List<Quality> =
        player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group ->
                (0 until group.length)
                    .filter { group.isTrackSupported(it) && group.getTrackFormat(it).height > 0 }
                    .map { Quality(group.getTrackFormat(it).height, group.mediaTrackGroup, it) }
            }
            .distinctBy { it.height }
            .sortedByDescending { it.height }

    /** Height picked by the viewer, null for automatic. */
    private fun selectedQuality(player: Player): Int? {
        val override = player.trackSelectionParameters.overrides.values
            .firstOrNull { it.type == C.TRACK_TYPE_VIDEO } ?: return null
        val index = override.trackIndices.firstOrNull() ?: return null
        return override.mediaTrackGroup.getFormat(index).height
    }

    private fun showQualityDialog() {
        val player = controller ?: return
        val qualities = videoQualities(player)
        val selected = selectedQuality(player)
        val labels = listOf(getString(R.string.player_quality_auto_hint)) + qualities.map { "${it.height}p" }
        val checked = qualities.indexOfFirst { it.height == selected } + 1
        dialogBuilder()
            .setTitle(R.string.player_quality)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                val quality = qualities.getOrNull(which - 1)
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .apply { quality?.let { setOverrideForType(TrackSelectionOverride(it.group, it.trackIndex)) } }
                    .build()
                updateTopBar()
                dialog.dismiss()
            }
            .show()
    }

    private fun showSleepDialog() {
        val player = controller ?: return
        val active = player.sessionExtras.getLong(PlaybackService.EXTRA_SLEEP_AT) > 0
        val minutes = player.sessionExtras.getInt(PlaybackService.EXTRA_SLEEP_MINUTES)
        val labels = listOf(getString(R.string.player_sleep_off)) +
            SLEEP_MINUTES.map { getString(R.string.player_sleep_minutes, it) }
        val checked = if (active) SLEEP_MINUTES.indexOf(minutes) + 1 else 0
        dialogBuilder()
            .setTitle(R.string.player_sleep_title)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                val chosen = SLEEP_MINUTES.getOrNull(which - 1) ?: 0
                player.sendCustomCommand(
                    SessionCommand(PlaybackService.COMMAND_SLEEP_TIMER, Bundle.EMPTY),
                    Bundle().apply { putInt(PlaybackService.ARG_MINUTES, chosen) }
                )
                if (chosen > 0) {
                    Toast.makeText(this, getString(R.string.player_sleep_set, chosen), Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun dialogBuilder() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    // --- Channel info ---

    private fun showChannelInfo() {
        binding.channelTitle.text = intent.getStringExtra(EXTRA_CHANNEL_NAME)
        showStationArtwork()
    }

    /** Shown by the player instead of a black screen when the stream has no picture. */
    private fun showStationArtwork() {
        ContextCompat.getDrawable(this, R.drawable.ic_radio_artwork)?.let { setArtwork(it) }
        val logoUrl = intent.getStringExtra(EXTRA_LOGO_URL) ?: return
        lifecycleScope.launch {
            val request = ImageRequest.Builder(this@PlayerActivity).data(logoUrl).build()
            val logo = SingletonImageLoader.get(this@PlayerActivity).execute(request).image
            logo?.asDrawable(resources)?.let { setArtwork(it) }
        }
    }

    private fun setArtwork(drawable: Drawable) {
        // The player scales artwork to its full height; a margin keeps the logo a sensible size
        binding.playerView.defaultArtwork = InsetDrawable(drawable, 0.3f)
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // --- Picture-in-picture ---

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Radio just keeps playing, also when it has a studio camera
        val player = controller ?: return
        if (!playsInBackground(player)) enterPipMode()
    }

    private fun enterPipMode() {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        enterPictureInPictureMode(params)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        binding.playerView.useController = !isInPictureInPictureMode
        if (isInPictureInPictureMode) binding.topBar.visibility = View.GONE
    }
}
