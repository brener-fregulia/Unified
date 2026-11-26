package unified.optimizations.fastquit

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FastQuit's lifecycle rules (fastquit-v1.md §6) with a stand-in "server thread": completion is the thread's end, a
 * wait never returns early (not even when interrupted), only one shutdown is pending, and a thread never waits on
 * itself. No sleeps decide anything: latches order the steps.
 */
class PendingSavesTest {
    private fun saver(release: CountDownLatch, saved: AtomicBoolean) = Thread({
        release.await()
        saved.set(true) // the "save" happens only after release
    }, "test saver").also { it.start() }

    @Test
    fun awaitReturnsOnlyAfterTheShutdownThreadEnded() {
        val release = CountDownLatch(1)
        val saved = AtomicBoolean()
        val thread = saver(release, saved)
        PendingSaves.begin(null, thread, "World A")
        assertTrue(PendingSaves.hasPending())
        assertEquals("World A", PendingSaves.pendingLevel())
        val waiter = Thread { PendingSaves.awaitAll(null) }
        waiter.start()
        assertFalse(waiter.join(java.time.Duration.ofMillis(200)), "still waiting while the save runs")
        waiter.interrupt() // an interrupt must not end the wait early
        assertFalse(waiter.join(java.time.Duration.ofMillis(200)))
        release.countDown()
        assertTrue(waiter.join(java.time.Duration.ofSeconds(10)))
        assertTrue(saved.get(), "the wait ended after the save, never before")
        assertFalse(PendingSaves.hasPending())
        assertNull(PendingSaves.pendingLevel())
    }

    @Test
    fun aSecondShutdownWaitsForTheFirstSoOnlyOneIsPending() {
        val firstRelease = CountDownLatch(1)
        val firstSaved = AtomicBoolean()
        val first = saver(firstRelease, firstSaved)
        PendingSaves.begin(null, first, "First")
        val secondRelease = CountDownLatch(1)
        val second = saver(secondRelease, AtomicBoolean())
        val tracker = Thread { PendingSaves.begin(null, second, "Second") }
        tracker.start()
        assertFalse(tracker.join(java.time.Duration.ofMillis(200)), "the second waits for the first")
        firstRelease.countDown()
        assertTrue(tracker.join(java.time.Duration.ofSeconds(10)))
        assertTrue(firstSaved.get())
        assertEquals("Second", PendingSaves.pendingLevel())
        secondRelease.countDown()
        PendingSaves.awaitAll(null)
        assertFalse(PendingSaves.hasPending())
    }

    @Test
    fun theSavingThreadNeverWaitsOnItself() {
        val done = CountDownLatch(1)
        val self = Thread {
            PendingSaves.awaitAll(null) // e.g. a storage access made by the stopping server itself
            done.countDown()
        }
        PendingSaves.begin(null, self, "Self")
        self.start()
        assertTrue(done.await(10, TimeUnit.SECONDS), "no self-deadlock")
        self.join()
        PendingSaves.awaitAll(null)
    }

    @Test
    fun aFinishedThreadIsNotPending() {
        val thread = Thread {}.also { it.start(); it.join() }
        val before = PendingSaves.trackedCount
        PendingSaves.begin(null, thread, "Done")
        assertEquals(before + 1, PendingSaves.trackedCount)
        assertFalse(PendingSaves.hasPending())
        PendingSaves.awaitAll(null)
    }
}
