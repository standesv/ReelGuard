package com.reelguard.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.reelguard.app.data.dao.BlockEventDao
import com.reelguard.app.data.dao.DailyStatsDao
import com.reelguard.app.data.entity.BlockEvent
import com.reelguard.app.data.entity.DailyStats

@Database(
    entities = [DailyStats::class, BlockEvent::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyStatsDao(): DailyStatsDao
    abstract fun blockEventDao(): BlockEventDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "reelguard.db"
                ).build().also { instance = it }
            }
    }
}
