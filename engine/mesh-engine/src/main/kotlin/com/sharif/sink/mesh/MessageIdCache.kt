package com.sharif.sink.mesh

import com.sharif.sink.protocol.MessageId

/**
 * Bounded recently-seen cache used for duplicate detection.
 *
 * A store-and-forward mesh that floods packets to all reachable neighbors
 * *will* deliver the same packet to a node more than once (loops in the
 * connectivity graph, retries, multiple relay paths). This is what stops a
 * duplicate from being reprocessed as a new message or forwarded again —
 * without it the mesh would broadcast-storm and the UI would show
 * duplicate messages.
 */
class MessageIdCache(
    private val maxEntries: Int = 2000,
    private val entryLifetimeMillis: Long = 24L * 60 * 60 * 1000,
) {
    private data class Entry(val id: MessageId, val seenAtEpochMillis: Long)

    private val order = ArrayDeque<Entry>()
    private val ids = HashSet<MessageId>()

    /** Returns true if [id] had not been seen before (and records it as seen). */
    @Synchronized
    fun observeAndCheckIfNew(id: MessageId, nowEpochMillis: Long): Boolean {
        evictExpired(nowEpochMillis)
        if (!ids.add(id)) return false
        order.addLast(Entry(id, nowEpochMillis))
        if (order.size > maxEntries) {
            val oldest = order.removeFirst()
            ids.remove(oldest.id)
        }
        return true
    }

    @Synchronized
    fun contains(id: MessageId): Boolean = ids.contains(id)

    @Synchronized
    fun size(): Int = ids.size

    private fun evictExpired(nowEpochMillis: Long) {
        while (order.isNotEmpty() && nowEpochMillis - order.first().seenAtEpochMillis > entryLifetimeMillis) {
            val expired = order.removeFirst()
            ids.remove(expired.id)
        }
    }
}
