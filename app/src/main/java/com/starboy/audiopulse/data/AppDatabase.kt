package com.starboy.audiopulse.data
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
// Type converters for Room
import androidx.room.TypeConverter
/**
 * Room database for storing headphone session data
 */
@Database(
    entities = [HeadphoneSession::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun sessionDao(): SessionDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "headphone_tracker_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}


class DatabaseConverters {
    
    @TypeConverter
    fun fromConnectionType(value: ConnectionType): String {
        return value.name
    }
    
    @TypeConverter
    fun toConnectionType(value: String): ConnectionType {
        return ConnectionType.valueOf(value)
    }
}
