package com.freemusic.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader as NpDownloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** An audio stream picked for a YouTube video, ready to be saved. */
class YouTubeAudio(val track: Track, val ext: String, val mime: String, val open: (onProgress: (done: Long, total: Long) -> Unit) -> InputStream)

/**
 * Finds the best audio-only stream of a YouTube video with NewPipeExtractor. YouTube changes its
 * player often, so when downloads start failing, bump the NewPipeExtractor version first.
 */
object YouTubeDownloader {
    private val http = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
    private const val CHUNK = 1_000_000L // YouTube throttles unbounded requests, so read in pieces

    private val initialized by lazy { NewPipe.init(Http(), Localization.DEFAULT); true }

    /** Blocking; call from an IO dispatcher. */
    fun resolve(videoId: String): YouTubeAudio {
        check(initialized)
        val info = StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
        val usable = info.audioStreams.filter { it.isUrl && !it.content.isNullOrBlank() }
        // MediaStore only takes m4a here; webm/opus is a fallback for videos that have no AAC stream.
        val isM4a = { s: org.schabi.newpipe.extractor.stream.AudioStream -> s.format?.mimeType?.contains("mp4") == true }
        val stream = (usable.filter(isM4a).ifEmpty { usable }).maxByOrNull { it.averageBitrate }
            ?: error("No audio stream found")
        val m4a = isM4a(stream)
        val track = Track(
            id = "yt:$videoId",
            title = info.name.orEmpty().ifBlank { "YouTube video" },
            artist = info.uploaderName.orEmpty().removeSuffix(" - Topic"),
            album = "",
            artUri = info.thumbnails.maxByOrNull { it.width }?.url,
            uri = "https://www.youtube.com/watch?v=$videoId",
            source = SOURCE_YOUTUBE,
            durationMs = info.duration * 1000,
            downloadable = true,
        )
        return YouTubeAudio(track, if (m4a) "m4a" else "ogg", if (m4a) "audio/mp4" else "audio/ogg") { onProgress ->
            ChunkedStream(stream.content, onProgress)
        }
    }

    /** Reads a stream with successive Range requests. */
    private class ChunkedStream(
        private val url: String,
        private val onProgress: (Long, Long) -> Unit,
    ) : InputStream() {
        private var buf = ByteArray(0)
        private var idx = 0
        private var next = 0L
        private var total = -1L

        override fun read(): Int {
            if (idx >= buf.size && !fill()) return -1
            return buf[idx++].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (idx >= buf.size && !fill()) return -1
            val n = minOf(len, buf.size - idx)
            System.arraycopy(buf, idx, b, off, n)
            idx += n
            return n
        }

        private fun fill(): Boolean {
            if (total in 0..next) return false
            val req = Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Range", "bytes=$next-${next + CHUNK - 1}")
                .build()
            http.newCall(req).execute().use { r ->
                if (r.code == 416) return false
                if (!r.isSuccessful) error("YouTube answered ${r.code}")
                r.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.let { total = it }
                buf = r.body!!.bytes()
            }
            idx = 0
            next += buf.size
            onProgress(next, total)
            if (total < 0 && buf.size < CHUNK) total = next
            return buf.isNotEmpty()
        }
    }

    private class Http : NpDownloader() {
        override fun execute(request: NpRequest): NpResponse {
            val b = Request.Builder().url(request.url())
            val body = request.dataToSend()?.toRequestBody()
            b.method(request.httpMethod(), body ?: if (request.httpMethod() == "POST") ByteArray(0).toRequestBody() else null)
            b.header("User-Agent", USER_AGENT)
            request.headers().forEach { (k, v) ->
                b.removeHeader(k)
                v.forEach { b.addHeader(k, it) }
            }
            http.newCall(b.build()).execute().use { r ->
                if (r.code == 429) throw ReCaptchaException("reCaptcha challenge requested", request.url())
                return NpResponse(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
            }
        }
    }
}
