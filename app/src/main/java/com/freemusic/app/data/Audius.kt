package com.freemusic.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

private const val APP_NAME = "FreeMusic"
private const val BASE = "https://api.audius.co/v1/"

fun audiusDownloadUrl(trackId: String) = "${BASE}tracks/${trackId.removePrefix("au:")}/download?app_name=$APP_NAME"

data class AudiusResponse(val data: List<AudiusTrack> = emptyList())

data class AudiusTrack(
    val id: String,
    val title: String,
    val duration: Int,
    val user: AudiusUser?,
    val artwork: AudiusArtwork?,
    @SerializedName("is_streamable") val isStreamable: Boolean = true,
    @SerializedName("is_downloadable") val isDownloadable: Boolean = false,
    val access: AudiusAccess? = null,
)

data class AudiusAccess(val stream: Boolean = true, val download: Boolean = false)

data class AudiusUser(val name: String?)

data class AudiusArtwork(
    @SerializedName("150x150") val small: String?,
    @SerializedName("480x480") val medium: String?,
    @SerializedName("1000x1000") val large: String?,
)

interface AudiusApi {
    @GET("tracks/trending")
    suspend fun trending(
        @Query("limit") limit: Int = 30,
        @Query("app_name") app: String = APP_NAME,
    ): AudiusResponse

    @GET("tracks/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("limit") limit: Int = 30,
        @Query("app_name") app: String = APP_NAME,
    ): AudiusResponse
}

/** Audius: free public API for streaming independent artists' music in third-party apps. */
class AudiusRepository {
    private val api = Retrofit.Builder()
        .baseUrl(BASE)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AudiusApi::class.java)

    suspend fun trending() = api.trending().data.mapNotNull(::toTrack)
    suspend fun search(query: String) = api.search(query).data.mapNotNull(::toTrack)

    private fun toTrack(t: AudiusTrack): Track? {
        if (!t.isStreamable) return null
        return Track(
            id = "au:${t.id}",
            title = t.title.unescape(),
            artist = t.user?.name?.unescape() ?: "Unknown artist",
            album = "",
            artUri = t.artwork?.medium ?: t.artwork?.large ?: t.artwork?.small,
            uri = "${BASE}tracks/${t.id}/stream?app_name=$APP_NAME",
            source = SOURCE_AUDIUS,
            durationMs = t.duration * 1000L,
            // Artists can disable downloads or put them behind a purchase; both must allow it.
            downloadable = t.isDownloadable && t.access?.download == true,
        )
    }
}
