package com.infinity.drive.core.telegram

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** No guessed message quota: uploads start immediately unless Telegram requested a wait.
 * Copies/edits use their own light pacing and never hold upload slots. The account-wide
 * server deadline is persisted by Android, including asynchronous send failures. */
class TelegramPacer(
    initialDeadline: Long = 0L,
    private val persistDeadline: (Long) -> Unit = {},
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private val mutationLock = Mutex()
    private var lastMutationAt = Long.MIN_VALUE / 2
    @Volatile private var floodUntil = initialDeadline
    val floodWaitSeconds: Int get() = ((floodUntil - now() + 999) / 1000).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    @Synchronized fun recordFloodWait(seconds: Int) {
        val until = now() + (seconds.coerceIn(1, 86_400) + 1L) * 1000
        if (until > floodUntil) { floodUntil = until; persistDeadline(until) }
    }

    /** Fail fast so waiting uploads return to the durable queue, freeing their slot. */
    suspend fun <T> upload(block: suspend () -> T): T {
        val wait = floodWaitSeconds
        if (wait > 0) throw TelegramException(429, "Waiting for Telegram: $wait seconds", wait)
        return captureWait(block)
    }

    suspend fun <T> paced(block: suspend () -> T): T = mutationLock.withLock {
        while (floodWaitSeconds > 0) delay((floodUntil - now()).coerceAtLeast(1))
        val gap = 400 - (now() - lastMutationAt)
        if (gap > 0) delay(gap)
        lastMutationAt = now()
        captureWait(block)
    }

    private suspend fun <T> captureWait(block: suspend () -> T): T = try { block() }
    catch (e: TelegramException) {
        if (e.isRateLimit) recordFloodWait(e.retryAfterSeconds ?: 5)
        throw e
    }
}
