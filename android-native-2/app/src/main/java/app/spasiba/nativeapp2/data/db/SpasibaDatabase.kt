package app.spasiba.nativeapp2.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        LikedSongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        SearchHistoryEntity::class,
        ListeningHistoryEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class SpasibaDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun likedSongDao(): LikedSongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun listeningHistoryDao(): ListeningHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: SpasibaDatabase? = null

        fun getDatabase(context: Context): SpasibaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpasibaDatabase::class.java,
                    "spasiba_native2_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
