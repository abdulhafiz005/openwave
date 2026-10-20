package com.freemusic.app.data

import com.freemusic.app.BuildConfig
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class JamendoResponse(val results: List<JamendoTrack> = emptyList())

data class JamendoTrack(
    val id: String,
    val name: String,
    val duration: Int,
    @SerializedName("artist_name") val artistName: String?,
    @SerializedName("album_name") val albumName: String?,
    val image: String?,
    val audio: String?,
)

interface JamendoApi {
    @GET("tracks/")
    suspend fun tracks(
        @Query("client_id") clientId: String = BuildConfig.JAMENDO_CLIENT_ID,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 30,
        @Query("order") order: String? = null,
        @Query("search") search: String? = null,
        @Query("imagesize") imageSize: Int = 300,
        @Query("audioformat") audioFormat: String = "mp32",
    ): JamendoResponse
}

class JamendoRepository {
    private val api = Retrofit.Builder()
        .baseUrl("https://api.jamendo.com/v3.0/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(JamendoApi::class.java)

    val configured get() = BuildConfig.JAMENDO_CLIENT_ID.isNotBlank()

    suspend fun trending() = fetch(order = "popularity_week")
    suspend fun newReleases() = fetch(order = "releasedate_desc")
    suspend fun search(query: String) = fetch(search = query, order = "relevance")

    private suspend fun fetch(order: String? = null, search: String? = null): List<Track> =
        api.tracks(order = order, search = search).results.mapNotNull {
            val stream = it.audio ?: return@mapNotNull null
            Track(
                id = "jm:${it.id}",
                title = it.name.unescape(),
                artist = it.artistName?.unescape() ?: "Unknown artist",
                album = it.albumName?.unescape() ?: "",
                artUri = it.image,
                uri = stream,
                source = SOURCE_JAMENDO,
                durationMs = it.duration * 1000L,
                downloadable = true,
            )
        }
}

internal fun String.unescape() = android.text.Html.fromHtml(this, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
