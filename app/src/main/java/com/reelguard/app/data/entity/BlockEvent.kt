package com.reelguard.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "block_events")
data class BlockEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val reason: String,   // "count_exceeded", "time_exceeded", "schedule", "focus"
    val bypassedAt: Long? = null  // Si l'utilisateur a contourné le blocage
)
