package com.sharif.sink.crypto.android

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

private const val PREFS_NAME = "sink_identity"
private const val KEY_DISPLAY_NAME = "display_name"
private const val KEY_SIGNING_PUBLIC = "signing_public_key"
private const val KEY_AGREEMENT_PUBLIC = "agreement_public_key"
private const val KEY_AGREEMENT_PRIVATE_WRAPPED = "agreement_private_key_wrapped"

/**
 * Persists the device identity's *public* material and the agreement
 * private key in its Keystore-wrapped (never plaintext) form. The signing
 * private key itself is never stored here at all — it lives exclusively
 * inside Android Keystore (see [AndroidSigningKeyProvider]).
 */
class EncryptedIdentityStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class PersistedIdentity(
        val displayName: String,
        val signingPublicKey: ByteArray,
        val agreementPublicKey: ByteArray,
        val wrappedAgreementPrivateKey: ByteArray,
    )

    fun load(): PersistedIdentity? {
        val displayName = prefs.getString(KEY_DISPLAY_NAME, null) ?: return null
        val signingPublic = prefs.getString(KEY_SIGNING_PUBLIC, null)?.decode() ?: return null
        val agreementPublic = prefs.getString(KEY_AGREEMENT_PUBLIC, null)?.decode() ?: return null
        val wrappedPrivate = prefs.getString(KEY_AGREEMENT_PRIVATE_WRAPPED, null)?.decode() ?: return null
        return PersistedIdentity(displayName, signingPublic, agreementPublic, wrappedPrivate)
    }

    fun save(identity: PersistedIdentity) {
        prefs.edit()
            .putString(KEY_DISPLAY_NAME, identity.displayName)
            .putString(KEY_SIGNING_PUBLIC, identity.signingPublicKey.encode())
            .putString(KEY_AGREEMENT_PUBLIC, identity.agreementPublicKey.encode())
            .putString(KEY_AGREEMENT_PRIVATE_WRAPPED, identity.wrappedAgreementPrivateKey.encode())
            .apply()
    }

    fun updateDisplayName(name: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, name).apply()
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
}
