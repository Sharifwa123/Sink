package com.sharif.sink.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sharif.sink.database.entity.PeerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(peer: PeerEntity)

    @Query("SELECT * FROM peers ORDER BY lastSeenEpochMillis DESC")
    fun observeAll(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE deviceId = :deviceId")
    suspend fun get(deviceId: String): PeerEntity?
}
