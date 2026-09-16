package com.sharif.sink.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RetryPolicyTest {

    @Test
    fun `delay grows exponentially with attempt number`() {
        val policy = RetryPolicy(baseDelayMillis = 1000, multiplier = 2.0, maxDelayMillis = 60_000)
        assertEquals(1000, policy.delayForAttempt(1))
        assertEquals(2000, policy.delayForAttempt(2))
        assertEquals(4000, policy.delayForAttempt(3))
        assertEquals(8000, policy.delayForAttempt(4))
    }

    @Test
    fun `delay is capped at maxDelayMillis`() {
        val policy = RetryPolicy(baseDelayMillis = 1000, multiplier = 2.0, maxDelayMillis = 5000)
        assertTrue(policy.delayForAttempt(10) <= 5000)
        assertEquals(5000, policy.delayForAttempt(10))
    }
}
