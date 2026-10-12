package com.sharif.sink.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sharif.sink.database.entity.PendingInviteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingInviteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(invite: PendingInviteEntity)

    @Query("SELECT * FROM pending_invites ORDER BY invitedAtEpochMillis DESC")
    fun observeAll(): Flow<List<PendingInviteEntity>>

    @Query("UPDATE pending_invites SET lastInviteSentEpochMillis = :sentAtEpochMillis WHERE phoneNumber = :phoneNumber")
    suspend fun markInviteResent(phoneNumber: String, sentAtEpochMillis: Long)

    @Query("DELETE FROM pending_invites WHERE phoneNumber = :phoneNumber")
    suspend fun delete(phoneNumber: String)
}
