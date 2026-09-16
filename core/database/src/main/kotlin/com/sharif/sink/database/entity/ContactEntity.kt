package com.sharif.sink.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A known peer's stable identity: public keys exchanged during discovery
 * or an explicit contact add. [isVerified] reflects the user having
 * compared [fingerprint] with the contact out-of-band (QR/safety number) —
 * see docs/SECURITY.md on what "verified" actually means and doesn't.
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    val signingPublicKey: ByteArray,
    val agreementPublicKey: ByteArray,
    val fingerprint: String,
    val isVerified: Boolean,
    val isBlocked: Boolean,
    val addedAtEpochMillis: Long,
    val phoneNumberForSms: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContactEntity) return false
        return deviceId == other.deviceId &&
            displayName == other.displayName &&
            signingPublicKey.contentEquals(other.signingPublicKey) &&
            agreementPublicKey.contentEquals(other.agreementPublicKey) &&
            fingerprint == other.fingerprint &&
            isVerified == other.isVerified &&
            isBlocked == other.isBlocked &&
            addedAtEpochMillis == other.addedAtEpochMillis &&
            phoneNumberForSms == other.phoneNumberForSms
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + signingPublicKey.contentHashCode()
        result = 31 * result + agreementPublicKey.contentHashCode()
        return result
    }
}
