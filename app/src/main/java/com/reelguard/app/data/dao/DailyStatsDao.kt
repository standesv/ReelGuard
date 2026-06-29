package com.reelguard.app.data.dao

import androidx.room.*
import com.reelguard.app.data.entity.DailyStats
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStatsDao {
    @Query("SELECT * FROM daily_stats ORDER BY date DESC LIMIT 30")
    fun getLast30Days(): Flow<List<DailyStats>>

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    suspend fun getByDate(date: String): DailyStats?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: DailyStats)

    @Query("SELECT SUM(totalTimeMs) FROM daily_stats WHERE date >= :since")
    suspend fun totalTimeSince(since: String): Long?

    @Query("SELECT SUM(totalReelCount) FROM daily_stats WHERE date >= :since")
    suspend fun totalCountSince(since: String): Int?

    @Query("DELETE FROM daily_stats WHERE date < :before")
    suspend fun deleteOlderThan(before: String)
}
