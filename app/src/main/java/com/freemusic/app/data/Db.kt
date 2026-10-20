package com.freemusic.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackIfAbsent(track: Track)

    @Query("UPDATE tracks SET liked = :liked WHERE id = :id")
    suspend fun setLiked(id: String, liked: Boolean)

    @Query("UPDATE tracks SET downloadedPath = :path WHERE id = :id")
    suspend fun setDownloaded(id: String, path: String?)

    @Query("SELECT * FROM tracks WHERE liked = 1")
    fun likedTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE downloadedPath IS NOT NULL")
    fun downloadedTracks(): Flow<List<Track>>

    @Query("SELECT * FROM playlists ORDER BY name")
    fun playlists(): Flow<List<Playlist>>

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id")
    suspend fun clearPlaylist(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToPlaylist(entry: PlaylistTrack)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeFromPlaylist(playlistId: Long, trackId: String)

    @Query(
        "SELECT t.* FROM tracks t JOIN playlist_tracks p ON t.id = p.trackId " +
            "WHERE p.playlistId = :playlistId ORDER BY p.addedAt"
    )
    fun playlistTracks(playlistId: Long): Flow<List<Track>>
}

@Database(entities = [Track::class, Playlist::class, PlaylistTrack::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): MusicDao

    companion object {
        @Volatile private var instance: AppDb? = null

        /** One instance per process, shared by the view model and the download service. */
        fun create(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "freemusic.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN downloadable INTEGER NOT NULL DEFAULT 0")
                // Jamendo tracks were always downloadable.
                db.execSQL("UPDATE tracks SET downloadable = 1 WHERE source = 'jamendo'")
            }
        }
    }
}
