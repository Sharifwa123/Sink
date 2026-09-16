package com.sharif.sink.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sharif.sink.database.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Query("SELECT * FROM contacts WHERE deviceId = :deviceId")
    suspend fun get(deviceId: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE isBlocked = 0 ORDER BY displayName ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("UPDATE contacts SET isVerified = 1 WHERE deviceId = :deviceId")
    suspend fun markVerified(deviceId: String)

    @Query("UPDATE contacts SET isBlocked = :blocked WHERE deviceId = :deviceId")
    suspend fun setBlocked(deviceId: String, blocked: Boolean)
}
