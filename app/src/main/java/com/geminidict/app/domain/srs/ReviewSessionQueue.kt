package com.geminidict.app.domain.srs

import com.geminidict.app.data.local.entity.WordbookEntryEntity

/**
 * Pure-Kotlin in-session review queue manager ensuring:
 * 1. Tapping AGAIN on ANY card (including the very last card) re-queues it inside the current session.
 * 2. Refreshing the queue from DB preserves un-recalled in-session relearn cards even though
 *    their DB `nextReviewAt` is 10 minutes in the future.
 */
object ReviewSessionQueue {

    data class SessionSnapshot(
        val queue: List<WordbookEntryEntity>,
        val currentIndex: Int,
        val relearnPool: Map<Long, WordbookEntryEntity>
    )

    fun onRated(
        queue: List<WordbookEntryEntity>,
        currentIndex: Int,
        relearnPool: Map<Long, WordbookEntryEntity>,
        updatedCard: WordbookEntryEntity,
        rating: ReviewRating
    ): SessionSnapshot {
        val nextQueue = queue.toMutableList()
        val nextPool = LinkedHashMap(relearnPool)

        if (rating == ReviewRating.AGAIN) {
            val insertPos = (currentIndex + 3).coerceAtMost(nextQueue.size)
            nextQueue.add(insertPos, updatedCard)
            nextPool[updatedCard.id] = updatedCard
        } else {
            nextPool.remove(updatedCard.id)
        }

        val nextIdx = currentIndex + 1
        return SessionSnapshot(
            queue = nextQueue,
            currentIndex = nextIdx,
            relearnPool = nextPool
        )
    }

    /**
     * Merges freshly queried DB due cards with any un-passed in-session relearn cards
     * so refreshing never drops cards waiting for in-session reinforcement.
     */
    fun mergeWithDue(
        dueFromDb: List<WordbookEntryEntity>,
        relearnPool: Map<Long, WordbookEntryEntity>
    ): List<WordbookEntryEntity> {
        val merged = LinkedHashMap<Long, WordbookEntryEntity>()
        for (item in dueFromDb) {
            merged[item.id] = item
        }
        for ((id, item) in relearnPool) {
            if (!merged.containsKey(id)) {
                merged[id] = item
            }
        }
        return merged.values.toList()
    }
}
