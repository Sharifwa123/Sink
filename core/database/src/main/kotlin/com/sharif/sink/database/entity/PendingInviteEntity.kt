package com.sharif.sink.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A phone number the user has manually added and invited to Sink, but who
 * has no known Sink identity yet (no HELLO handshake ever happened with
 * them). Distinct from [ContactEntity], which always represents a real,
 * discovered Sink device — this exists so a phone number can be reachable
 * and visible in Contacts *before* that device ever runs Sink, matching an
 * "invite a friend" flow rather than claiming a mesh connection that
 * doesn't exist. See docs/IMPLEMENTATION_STATUS.md.
 */
@Entity(tableName = "pending_invites")
data class PendingInviteEntity(
    @PrimaryKey val phoneNumber: String,
    val displayName: String,
    val invitedAtEpochMillis: Long,
    val lastInviteSentEpochMillis: Long,
)
