package com.inkagent.agent

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class UiMutationTest {
    @Test fun queuedWriteThatTimesOutCannotMutateTheEditorWhenDrained() {
        val items = mutableListOf("existing")
        val edit = UiMutation { items.add("late") }

        assertThrows(IllegalStateException::class.java) { edit.awaitStart(0, TimeUnit.NANOSECONDS) }
        edit.run()
        assertEquals(listOf("existing"), items)
    }

    @Test fun claimedWriteReportsRealResultEvenPastDeadlineAndInterrupt() {
        val claimed = CountDownLatch(1)
        val release = CountDownLatch(1)
        val enteringAwait = CountDownLatch(1)
        val result = AtomicReference<Result<String>>()
        val interruptAfterResult = AtomicReference<Boolean>()
        val edit = UiMutation {
            claimed.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            "committed"
        }
        val ui = thread(isDaemon = true) { edit.run() }
        assertTrue(claimed.await(5, TimeUnit.SECONDS))
        val worker = thread(isDaemon = true) {
            Thread.currentThread().interrupt()
            enteringAwait.countDown()
            result.set(runCatching { edit.awaitStart(0, TimeUnit.NANOSECONDS) })
            interruptAfterResult.set(Thread.currentThread().isInterrupted)
        }
        try {
            assertTrue(enteringAwait.await(5, TimeUnit.SECONDS))
            // Wait for the real indefinite latch await; release must come after timeout+interrupt.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (worker.state != Thread.State.WAITING && worker.isAlive && System.nanoTime() < deadline) {
                Thread.yield()
            }
            assertEquals(Thread.State.WAITING, worker.state)
            assertNull(result.get())
        } finally {
            release.countDown()
            ui.join(5000)
            worker.join(5000)
        }
        assertEquals("committed", result.get()?.getOrThrow())
        assertEquals(true, interruptAfterResult.get())
    }

    @Test fun completionErrorIsNotMistakenForAWorkerInterrupt() {
        val failure = InterruptedException("mutation failed before committing")
        val edit = UiMutation<String> { throw failure }
        edit.run()
        assertSame(failure, assertThrows(InterruptedException::class.java) {
            edit.awaitStart(0, TimeUnit.NANOSECONDS)
        })
        assertFalse(Thread.currentThread().isInterrupted)
    }
}
