package com.reelguard.app.data.dao

import androidx.room.*
import com.reelguard.app.data.entity.BlockEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockEventDao {
    @Query("SELECT * FROM block_events ORDER BY timestamp DESC LIMIT 50")
    fun getRecent(): Flow<List<BlockEvent>>

    @Insert
    suspend fun insert(event: BlockEvent)

    @Query("SELECT COUNT(*) FROM block_events WHERE timestamp >= :since")
    suspend fun countSince(since: Long): Int
}
