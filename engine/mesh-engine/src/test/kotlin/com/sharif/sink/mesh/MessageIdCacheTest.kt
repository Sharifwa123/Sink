package com.sharif.sink.mesh

import com.sharif.sink.protocol.MessageId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageIdCacheTest {

    @Test
    fun `first observation of an id is new`() {
        val cache = MessageIdCache()
        assertTrue(cache.observeAndCheckIfNew(MessageId.new(), nowEpochMillis = 0))
    }

    @Test
    fun `repeated observation of the same id is not new`() {
        val cache = MessageIdCache()
        val id = MessageId.new()
        assertTrue(cache.observeAndCheckIfNew(id, nowEpochMillis = 0))
        assertFalse(cache.observeAndCheckIfNew(id, nowEpochMillis = 1))
        assertFalse(cache.observeAndCheckIfNew(id, nowEpochMillis = 2))
    }

    @Test
    fun `entries evict after their lifetime elapses`() {
        val cache = MessageIdCache(entryLifetimeMillis = 100)
        val id = MessageId.new()
        cache.observeAndCheckIfNew(id, nowEpochMillis = 0)
        assertTrue(cache.contains(id))

        // Still within lifetime.
        cache.observeAndCheckIfNew(MessageId.new(), nowEpochMillis = 50)
        assertTrue(cache.contains(id))

        // Past lifetime: a later observation should evict it and treat it as new again.
        assertTrue(cache.observeAndCheckIfNew(id, nowEpochMillis = 500))
    }

    @Test
    fun `bounded size evicts oldest entries first`() {
        val cache = MessageIdCache(maxEntries = 2)
        val first = MessageId.new()
        val second = MessageId.new()
        val third = MessageId.new()

        cache.observeAndCheckIfNew(first, nowEpochMillis = 0)
        cache.observeAndCheckIfNew(second, nowEpochMillis = 0)
        cache.observeAndCheckIfNew(third, nowEpochMillis = 0)

        assertFalse(cache.contains(first))
        assertTrue(cache.contains(second))
        assertTrue(cache.contains(third))
    }
}
