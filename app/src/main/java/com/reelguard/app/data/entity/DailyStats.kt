package com.reelguard.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_stats")
data class DailyStats(
    @PrimaryKey val date: String,           // "2024-06-29"
    val totalReelCount: Int = 0,
    val totalTimeMs: Long = 0L,
    val quotaMetCount: Boolean = false,
    val quotaMetTime: Boolean = false,
    val streakDay: Int = 0
)
