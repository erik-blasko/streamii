package com.erikblasko.tvstreamapp.data.network

import com.erikblasko.tvstreamapp.data.model.Channel
import com.erikblasko.tvstreamapp.data.model.StreamSource
import com.erikblasko.tvstreamapp.data.model.StreamStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class StreamChecker(private val baseClient: OkHttpClient) {

    private val _urlStatusMap = MutableStateFlow<Map<String, StreamStatus>>(emptyMap())
    val urlStatusMap: StateFlow<Map<String, StreamStatus>> = _urlStatusMap.asStateFlow()

    private val checkClient = baseClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Results are collected here and published in batches, so the UI
    // recomposes a few times per second instead of once per checked URL.
    private val pendingResults = ConcurrentHashMap<String, StreamStatus>()

    // Source of each URL being checked, for the headers some streams require
    @Volatile
    private var sourcesByUrl: Map<String, StreamSource> = emptyMap()

    // Work queue, guarded by queueLock. URLs of priority channels (what the user
    // currently sees / has filtered) are taken first, the rest in original order.
    private val queueLock = Any()
    private val queue = ArrayDeque<String>()
    private val notStarted = HashSet<String>()
    private var priorityChannels: List<Channel> = emptyList()
    private var priorityStart = 0
    private var priorityOffset = 0

    /**
     * Checks [channels] first, starting at [startIndex] and wrapping around.
     * Already started or finished checks are not affected.
     */
    fun prioritize(channels: List<Channel>, startIndex: Int = 0) = synchronized(queueLock) {
        priorityChannels = channels
        priorityStart = if (channels.isEmpty()) 0 else startIndex.coerceIn(0, channels.size - 1)
        priorityOffset = 0
    }

    private fun nextUrl(): String? = synchronized(queueLock) {
        val channels = priorityChannels
        while (priorityOffset < channels.size) {
            val channel = channels[(priorityStart + priorityOffset) % channels.size]
            for (source in channel.sources) {
                if (notStarted.remove(source.url)) return source.url
            }
            priorityOffset++
        }
        while (queue.isNotEmpty()) {
            val url = queue.removeFirst()
            if (notStarted.remove(url)) return url
        }
        null
    }

    fun reset() {
        synchronized(queueLock) {
            queue.clear()
            notStarted.clear()
        }
        pendingResults.clear()
        _urlStatusMap.value = emptyMap()
    }

    fun channelStatus(
        channel: Channel,
        statuses: Map<String, StreamStatus> = _urlStatusMap.value
    ): StreamStatus {
        var anyChecking = false
        var allOffline = true
        for (source in channel.sources) {
            when (statuses[source.url]) {
                StreamStatus.ONLINE -> return StreamStatus.ONLINE
                StreamStatus.CHECKING -> { anyChecking = true; allOffline = false }
                StreamStatus.OFFLINE -> {}
                else -> allOffline = false
            }
        }
        return when {
            anyChecking -> StreamStatus.CHECKING
            allOffline -> StreamStatus.OFFLINE
            else -> StreamStatus.UNKNOWN
        }
    }

    fun sourceStatus(url: String): StreamStatus {
        return _urlStatusMap.value[url] ?: StreamStatus.UNKNOWN
    }

    suspend fun checkAll(channels: List<Channel>) = coroutineScope {
        val sources = channels.flatMap { it.sources }.distinctBy { it.url }
        val allUrls = sources.map { it.url }
        sourcesByUrl = sources.associateBy { it.url }

        synchronized(queueLock) {
            queue.clear()
            queue.addAll(allUrls)
            notStarted.clear()
            notStarted.addAll(allUrls)
            priorityOffset = 0
        }

        _urlStatusMap.update { current ->
            current + allUrls.associateWith { StreamStatus.CHECKING }
        }

        val publisher = launch(Dispatchers.Default) {
            while (true) {
                delay(PUBLISH_INTERVAL_MS)
                publishPending()
            }
        }

        try {
            coroutineScope {
                repeat(PARALLEL_CHECKS) {
                    launch(Dispatchers.IO) {
                        while (isActive) {
                            val url = nextUrl() ?: break
                            pendingResults[url] = checkSingle(url)
                        }
                    }
                }
            }
        } finally {
            publisher.cancel()
            // A cancelled run must not overwrite statuses of a newer run
            if (isActive) publishPending()
        }
    }

    private fun publishPending() {
        if (pendingResults.isEmpty()) return
        val batch = HashMap<String, StreamStatus>()
        for (url in pendingResults.keys) {
            pendingResults.remove(url)?.let { batch[url] = it }
        }
        if (batch.isNotEmpty()) _urlStatusMap.update { it + batch }
    }

    /**
     * Checks a stream roughly the way the player would open it: follows redirects,
     * and for HLS goes through the playlist down to the first media segment, because
     * a playlist often loads fine while its segments are geo-blocked or gone.
     */
    private suspend fun checkSingle(url: String): StreamStatus = withContext(Dispatchers.IO) {
        try {
            val source = sourcesByUrl[url]
            if (isPlayable(url.toHttpUrl(), source, depth = 0)) StreamStatus.ONLINE else StreamStatus.OFFLINE
        } catch (_: Exception) {
            StreamStatus.OFFLINE
        }
    }

    private fun isPlayable(url: HttpUrl, source: StreamSource?, depth: Int): Boolean {
        val request = streamRequest(url, source).build()
        checkClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            val contentType = body.contentType()?.toString().orEmpty()
            if (contentType.contains("html", ignoreCase = true)) return false

            val stream = body.source()
            stream.request(HEADER_PEEK_BYTES)
            if (stream.buffer.exhausted()) return false
            val head = stream.buffer.snapshot(minOf(stream.buffer.size, HEADER_PEEK_BYTES).toInt()).utf8()
            val isHls = head.trimStart('\uFEFF', ' ', '\r', '\n', '\t').startsWith(HLS_HEADER)
            if (!isHls) return true // TS, DASH, MP4... the server is sending data

            stream.request(MAX_PLAYLIST_BYTES)
            val playlist = stream.buffer.readUtf8(minOf(stream.buffer.size, MAX_PLAYLIST_BYTES))
            val next = firstUri(playlist) ?: return false
            val nextUrl = response.request.url.resolve(next) ?: return false

            return if (playlist.contains("#EXT-X-STREAM-INF") && depth < MAX_PLAYLIST_DEPTH) {
                isPlayable(nextUrl, source, depth + 1) // master playlist -> first variant
            } else {
                isSegmentAvailable(nextUrl, source)
            }
        }
    }

    private fun isSegmentAvailable(url: HttpUrl, source: StreamSource?): Boolean {
        val request = streamRequest(url, source)
            .header("Range", "bytes=0-1023")
            .build()
        checkClient.newCall(request).execute().use { response ->
            return response.isSuccessful &&
                response.body?.contentType()?.toString()?.contains("html", ignoreCase = true) != true
        }
    }

    /** Request with the same headers the player sends for this source. */
    private fun streamRequest(url: HttpUrl, source: StreamSource?): Request.Builder =
        Request.Builder().url(url)
            .header("User-Agent", source?.userAgent ?: StreamSource.DEFAULT_USER_AGENT)
            .apply { source?.referrer?.let { header("Referer", it) } }

    /** First non-comment line of an m3u8 playlist (variant playlist or media segment). */
    private fun firstUri(playlist: String): String? =
        playlist.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }

    private companion object {
        const val PUBLISH_INTERVAL_MS = 500L
        const val PARALLEL_CHECKS = 20
        const val HLS_HEADER = "#EXTM3U"
        const val HEADER_PEEK_BYTES = 16L
        const val MAX_PLAYLIST_BYTES = 256L * 1024
        const val MAX_PLAYLIST_DEPTH = 2
    }
}
