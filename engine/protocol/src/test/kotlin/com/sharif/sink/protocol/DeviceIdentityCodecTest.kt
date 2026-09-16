package com.sharif.sink.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceIdentityCodecTest {

    @Test
    fun `device identity survives encode-decode round trip`() {
        val original = DeviceIdentity(
            deviceId = DeviceId("12345 67890"),
            signingPublicKey = byteArrayOf(1, 2, 3, 4, 5),
            agreementPublicKey = byteArrayOf(6, 7, 8, 9),
            displayName = "Alice",
            capabilities = setOf(TransportKind.LOCAL_MESH, TransportKind.SMS),
        )

        val decoded = DeviceIdentityCodec.decode(DeviceIdentityCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `empty capabilities round trip`() {
        val original = DeviceIdentity(
            deviceId = DeviceId("device"),
            signingPublicKey = byteArrayOf(1),
            agreementPublicKey = byteArrayOf(2),
            displayName = "",
            capabilities = emptySet(),
        )

        val decoded = DeviceIdentityCodec.decode(DeviceIdentityCodec.encode(original))

        assertEquals(original, decoded)
    }
}
