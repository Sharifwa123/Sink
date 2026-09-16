package com.sharif.sink.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Runtime-ish peer visibility, persisted mainly for the diagnostics screen
 * and "last seen" history — not the source of truth for live connection
 * state, which lives in [com.sharif.sink.mesh.TransportManager] in memory.
 */
@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    val lastSeenEpochMillis: Long,
    val connectionState: String,
    val transport: String,
)
