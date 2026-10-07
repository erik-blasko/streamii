package com.erikblasko.tvstreamapp.playback

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.erikblasko.tvstreamapp.PlayerActivity
import com.erikblasko.tvstreamapp.StreamiiApp
import com.erikblasko.tvstreamapp.data.model.StreamSource
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Owns the player, so radio keeps playing with the screen off or the app in the background;
 * the media notification and lock screen controls come with the session.
 * [PlayerActivity] connects to it with a MediaController.
 *
 * A channel is played with all its sources: when one fails, the next one takes over.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    companion object {
        /** Controller -> service: start the sleep timer ([ARG_MINUTES] > 0) or cancel it (0) */
        const val COMMAND_SLEEP_TIMER = "sleep_timer"
        const val ARG_MINUTES = "minutes"

        /** Service -> controllers: a source failed, [EXTRA_SOURCE_INDEX] plays now */
        const val COMMAND_SOURCE_SWITCHED = "source_switched"

        // Session extras, read by the player screen
        /** Elapsed realtime when the sleep timer pauses playback, 0 without a timer */
        const val EXTRA_SLEEP_AT = "sleep_at"
        const val EXTRA_SLEEP_MINUTES = "sleep_minutes"
        const val EXTRA_SOURCE_INDEX = "source_index"
        const val EXTRA_SOURCE_COUNT = "source_count"

        /** How far behind the live edge streams play with the delay on */
        const val LIVE_DELAY_MS = 30_000L

        private const val EXTRA_URLS = "urls"
        private const val EXTRA_USER_AGENTS = "user_agents"
        private const val EXTRA_REFERRERS = "referrers"
        private const val EXTRA_IS_RADIO = "is_radio"

        /**
         * Media item for a channel; its id is the channel id. Items sent from a controller lose
         * their URI, so the sources travel in the request metadata with the headers they need.
         */
        fun mediaItem(
            channelId: String,
            name: String,
            logoUrl: String?,
            sources: List<StreamSource>,
            isRadio: Boolean
        ): MediaItem = MediaItem.Builder()
            .setMediaId(channelId)
            .setUri(sources.first().url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtworkUri(logoUrl?.let(Uri::parse))
                    .build()
            )
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(Uri.parse(sources.first().url))
                    .setExtras(sourcesBundle(sources).apply { putBoolean(EXTRA_IS_RADIO, isRadio) })
                    .build()
            )
            .build()

        /** Sources as parallel string lists; "" stands for a header the source does not need. */
        fun sourcesBundle(sources: List<StreamSource>): Bundle = Bundle().apply {
            putStringArrayList(EXTRA_URLS, ArrayList(sources.map { it.url }))
            putStringArrayList(EXTRA_USER_AGENTS, ArrayList(sources.map { it.userAgent.orEmpty() }))
            putStringArrayList(EXTRA_REFERRERS, ArrayList(sources.map { it.referrer.orEmpty() }))
        }

        fun sourcesFrom(bundle: Bundle?): List<StreamSource> {
            val urls = bundle?.getStringArrayList(EXTRA_URLS) ?: return emptyList()
            val userAgents = bundle.getStringArrayList(EXTRA_USER_AGENTS)
            val referrers = bundle.getStringArrayList(EXTRA_REFERRERS)
            return urls.mapIndexed { index, url ->
                StreamSource(
                    url = url,
                    quality = null,
                    playlistName = "",
                    userAgent = userAgents?.getOrNull(index)?.takeIf { it.isNotEmpty() },
                    referrer = referrers?.getOrNull(index)?.takeIf { it.isNotEmpty() }
                )
            }
        }
    }

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(15_000)
        .setUserAgent(StreamSource.DEFAULT_USER_AGENT)

    /** The channel's item as the controller sent it, with all its sources */
    private var channelItem: MediaItem? = null
    private var sources: List<StreamSource> = emptyList()
    private var sourceIndex = 0

    /** Streams without a recognizable extension get one more try as HLS before the next source */
    private var triedAsHls = false

    private var liveDelay = false

    private var sleepAt = 0L
    private var sleepMinutes = 0
    private val sleepRunnable = Runnable {
        player.pause()
        setSleepTimer(0)
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            // Pause when headphones are unplugged; keep Wi-Fi and CPU awake with the screen off
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        // Debug builds log track selection and format changes to logcat (tag EventLogger)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            player.addAnalyticsListener(EventLogger())
        }
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onPlaybackError(error)
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                // Closing the player ends a sleep timer too
                if (timeline.isEmpty && sleepAt != 0L) setSleepTimer(0)
            }
        })

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .build()

        val preferences = (application as StreamiiApp).container.preferences
        scope.launch {
            preferences.liveDelay.distinctUntilChanged().collect { delay ->
                val changed = delay != liveDelay
                liveDelay = delay
                // Applies to the live stream that is playing, which restarts at the new offset
                if (changed && player.isCurrentMediaItemLive) playSource(sourceIndex)
            }
        }
    }

    private fun onPlaybackError(error: PlaybackException) {
        val item = player.currentMediaItem ?: return
        when {
            // After a long pause the live window has moved on: continue from the live edge
            error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                player.seekToDefaultPosition()
                player.prepare()
            }
            !triedAsHls -> {
                triedAsHls = true
                player.setMediaSource(HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(item))
                player.prepare()
                player.play()
            }
            sourceIndex < sources.lastIndex -> {
                playSource(sourceIndex + 1)
                mediaSession?.broadcastCustomCommand(
                    SessionCommand(COMMAND_SOURCE_SWITCHED, Bundle.EMPTY),
                    Bundle().apply {
                        putInt(EXTRA_SOURCE_INDEX, sourceIndex)
                        putInt(EXTRA_SOURCE_COUNT, sources.size)
                    }
                )
            }
            // Every source failed: the error stays on screen
        }
    }

    /** Plays the channel from source [index]. */
    private fun playSource(index: Int) {
        val item = channelItem ?: return
        sourceIndex = index
        player.setMediaItem(playableItem(item, index))
        player.prepare()
        player.play()
        updateSessionExtras()
    }

    /** The channel's item for source [index], with that source's headers set on the data source. */
    private fun playableItem(item: MediaItem, index: Int): MediaItem {
        val source = sources[index]
        triedAsHls = false
        // One stream plays at a time, so its headers can be the factory's defaults
        httpDataSourceFactory
            .setUserAgent(source.userAgent ?: StreamSource.DEFAULT_USER_AGENT)
            .setDefaultRequestProperties(source.referrer?.let { mapOf("Referer" to it) } ?: emptyMap())
        return item.buildUpon()
            .setUri(source.url)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(if (liveDelay) LIVE_DELAY_MS else C.TIME_UNSET)
                    .build()
            )
            .build()
    }

    private fun setSleepTimer(minutes: Int) {
        handler.removeCallbacks(sleepRunnable)
        sleepMinutes = minutes
        sleepAt = if (minutes > 0) SystemClock.elapsedRealtime() + minutes * 60_000L else 0L
        if (minutes > 0) handler.postDelayed(sleepRunnable, minutes * 60_000L)
        updateSessionExtras()
    }

    private fun updateSessionExtras() {
        mediaSession?.setSessionExtras(Bundle().apply {
            putLong(EXTRA_SLEEP_AT, sleepAt)
            putInt(EXTRA_SLEEP_MINUTES, sleepMinutes)
            putInt(EXTRA_SOURCE_INDEX, sourceIndex)
            putInt(EXTRA_SOURCE_COUNT, sources.size)
        })
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(COMMAND_SLEEP_TIMER, Bundle.EMPTY))
                .build()
            // Live TV has no use for playback speed; this removes it from the player's settings
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .remove(Player.COMMAND_SET_SPEED_AND_PITCH)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == COMMAND_SLEEP_TIMER) {
                setSleepTimer(args.getInt(ARG_MINUTES))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            // The player screen sends one channel at a time
            val item = mediaItems.first()
            val itemSources = sourcesFrom(item.requestMetadata.extras)
            if (itemSources.isEmpty()) return Futures.immediateFuture(mediaItems)
            channelItem = item
            sources = itemSources
            sourceIndex = 0
            setNotificationTarget(item)
            updateSessionExtras()
            return Futures.immediateFuture(mutableListOf(playableItem(item, 0)))
        }
    }

    /** Tapping the notification opens the player for the channel that is playing. */
    private fun setNotificationTarget(item: MediaItem) {
        val intent = PlayerActivity.intent(
            context = this,
            channelId = item.mediaId,
            sources = sources,
            name = item.mediaMetadata.title?.toString().orEmpty(),
            logoUrl = item.mediaMetadata.artworkUri?.toString(),
            isRadio = item.requestMetadata.extras?.getBoolean(EXTRA_IS_RADIO) ?: false,
            resume = true
        ).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        mediaSession?.setSessionActivity(
            PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away keeps a playing radio going, anything else stops
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(sleepRunnable)
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

/** Whether a stream with these tracks is sound only (null while the tracks are not known yet). */
internal fun Player.isAudioOnly(): Boolean? {
    val tracks = currentTracks
    val hasVideo = tracks.containsType(C.TRACK_TYPE_VIDEO)
    val hasAudio = tracks.containsType(C.TRACK_TYPE_AUDIO)
    return when {
        hasVideo -> false
        hasAudio -> true
        else -> null
    }
}
