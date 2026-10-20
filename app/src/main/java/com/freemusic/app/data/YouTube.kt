package com.freemusic.app.data

import com.freemusic.app.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class Video(val id: String, val title: String, val channel: String, val thumbnail: String?)

data class YtSearchResponse(val items: List<YtItem> = emptyList())
data class YtItem(val id: YtId?, val snippet: YtSnippet?)
data class YtId(val videoId: String?)
data class YtSnippet(val title: String?, val channelTitle: String?, val thumbnails: Map<String, YtThumb>?)
data class YtThumb(val url: String?)

interface YouTubeApi {
    @GET("search")
    suspend fun search(
        @Query("q") q: String,
        @Query("key") key: String = BuildConfig.YOUTUBE_API_KEY,
        @Query("part") part: String = "snippet",
        @Query("type") type: String = "video",
        @Query("videoEmbeddable") embeddable: String = "true",
        @Query("videoCategoryId") category: String = "10",
        @Query("maxResults") max: Int = 25,
    ): YtSearchResponse
}

class YouTubeRepository {
    private val api = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/youtube/v3/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(YouTubeApi::class.java)

    val searchConfigured get() = BuildConfig.YOUTUBE_API_KEY.isNotBlank()

    suspend fun search(query: String): List<Video> = api.search(query).items.mapNotNull {
        val id = it.id?.videoId ?: return@mapNotNull null
        val sn = it.snippet
        Video(
            id = id,
            title = (sn?.title ?: "Untitled").unescape(),
            channel = (sn?.channelTitle ?: "").unescape(),
            thumbnail = sn?.thumbnails?.get("medium")?.url ?: sn?.thumbnails?.values?.firstOrNull()?.url,
        )
    }

    companion object {
        private val patterns = listOf(
            Regex("""[?&]v=([A-Za-z0-9_-]{11})"""),
            Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
            Regex("""youtube\.com/(?:shorts|embed|live)/([A-Za-z0-9_-]{11})"""),
            Regex("""^([A-Za-z0-9_-]{11})$"""),
        )

        /** Returns a video id if the text is a YouTube link or a bare 11-character id. */
        fun parseVideoId(text: String): String? {
            val t = text.trim()
            return patterns.firstNotNullOfOrNull { it.find(t)?.groupValues?.get(1) }
        }
    }
}
