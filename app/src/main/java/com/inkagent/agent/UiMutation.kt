package com.inkagent.agent

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * A deadline to START a mutation, not a deadline to report success. Returning a timeout
 * after the UI has claimed the operation would let callers retry an edit that can still commit.
 * Readers should keep using onMain: unlike edits, they do not need to await a claimed operation.
 */
internal class UiMutation<T>(private val block: () -> T) : Runnable {
    private enum class State { QUEUED, RUNNING, EXPIRED }
    private val state = AtomicReference(State.QUEUED)
    private val completed = CountDownLatch(1)
    private var result: Result<T>? = null

    override fun run() {
        if (!state.compareAndSet(State.QUEUED, State.RUNNING)) return
        try {
            result = runCatching(block)
        } finally {
            completed.countDown()
        }
    }

    fun awaitStart(timeout: Long, unit: TimeUnit): T {
        var requestedInterrupt = false
        try {
            val finished = try {
                completed.await(timeout, unit)
            } catch (_: InterruptedException) {
                requestedInterrupt = true
                false
            }
            if (finished) return result!!.getOrThrow()
            if (state.compareAndSet(State.QUEUED, State.EXPIRED)) {
                throw IllegalStateException("UI 写入未开始，已取消")
            }
            // It is too late to tell the caller that nothing was written. Preserve the
            // interrupt for their next check, but deliver this mutation's real outcome.
            while (true) {
                try {
                    completed.await()
                    break
                } catch (_: InterruptedException) {
                    requestedInterrupt = true
                }
            }
            return result!!.getOrThrow()
        } finally {
            if (requestedInterrupt) Thread.currentThread().interrupt()
        }
    }
}
