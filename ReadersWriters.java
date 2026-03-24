import java.util.concurrent.Semaphore;
import java.util.Random;

/**
 * ============================================================
 *  READERS-WRITERS PROBLEM (First Readers Preference)
 *  BSc Mathematics & Computer Science — Operating Systems II
 * ============================================================
 *
 *  PROBLEM STATEMENT:
 *  ------------------
 *  A shared resource (e.g. a database) can be accessed by:
 *    - MULTIPLE readers simultaneously (read-only, safe)
 *    - Only ONE writer at a time (exclusive access)
 *    - No reader and writer at the same time
 *
 *  VARIANT: First Readers Preference
 *    - No reader waits unless a WRITER currently holds the lock
 *    - New readers can enter even while earlier readers are inside
 *    - Trade-off: writers may starve if readers keep arriving
 *
 *  SOLUTION — Two Semaphores + readerCount:
 *  -----------------------------------------
 *    writeLock   = 1   Exclusive access to the data.
 *                      Writers always acquire this.
 *                      First reader acquires it  (locks out writers).
 *                      Last reader releases it   (lets writers in).
 *
 *    mutex       = 1   Protects readerCount — ensures the
 *                      increment/decrement is atomic.
 *
 *    readerCount = 0   Tracks how many readers are currently inside.
 *
 *  READER ENTRY/EXIT PATTERN:
 *    Entry: wait(mutex) → readerCount++
 *           if first reader: wait(writeLock)    ← block writers
 *           signal(mutex)
 *           [read data]
 *    Exit:  wait(mutex) → readerCount--
 *           if last reader: signal(writeLock)   ← allow writers
 *           signal(mutex)
 *
 *  KEY INSIGHT:
 *    Only the FIRST reader acquires writeLock (to block writers).
 *    Only the LAST reader releases writeLock (to unblock writers).
 *    All readers in between ride for free — zero wait for them.
 * ============================================================
 */
public class ReadersWriters {

    // ── Configuration ────────────────────────────────────────────────────────
    static final int NUM_READERS = 5;
    static final int NUM_WRITERS = 2;
    static final int ITERATIONS  = 3;   // how many times each reads/writes

    // ── Semaphores ───────────────────────────────────────────────────────────

    /**
     * writeLock: binary semaphore — exclusive access to the shared data.
     * Init = 1 (data is free).
     * Writers acquire this directly before writing, release after.
     * First reader acquires this (blocks writers).
     * Last reader releases this (unblocks writers).
     */
    static final Semaphore writeLock = new Semaphore(1);

    /**
     * mutex: binary semaphore — protects readerCount.
     * Init = 1 (unlocked).
     * Any reader must hold this while incrementing/decrementing readerCount.
     */
    static final Semaphore mutex = new Semaphore(1);

    // ── Shared state ─────────────────────────────────────────────────────────
    static int readerCount = 0;     // how many readers are currently inside
    static int sharedData  = 0;     // the shared resource

    // =========================================================================
    //  READER THREAD
    // =========================================================================
    static class Reader extends Thread {
        private final int id;
        private final Random rand = new Random();

        Reader(int id) {
            this.id = id;
            setName("Reader-" + id);
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < ITERATIONS; i++) {
                    Thread.sleep(rand.nextInt(600) + 200);

                    // ── ENTRY SECTION ───────────────────────────────────────
                    mutex.acquire();            // wait(mutex) — lock readerCount
                    readerCount++;
                    if (readerCount == 1) {
                        // First reader: block all writers
                        writeLock.acquire();    // wait(writeLock)
                        log("FIRST reader in → writeLock acquired, writers blocked");
                    }
                    mutex.release();            // signal(mutex)

                    // ── CRITICAL SECTION — Read ─────────────────────────────
                    log("READING  sharedData=" + sharedData
                            + " (active readers: " + readerCount + ")");
                    Thread.sleep(rand.nextInt(400) + 100);

                    // ── EXIT SECTION ────────────────────────────────────────
                    mutex.acquire();            // wait(mutex)
                    readerCount--;
                    if (readerCount == 0) {
                        // Last reader: unblock writers
                        writeLock.release();    // signal(writeLock)
                        log("LAST reader out → writeLock released, writers may enter");
                    }
                    mutex.release();            // signal(mutex)
                }
                log("DONE reading.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // =========================================================================
    //  WRITER THREAD
    // =========================================================================
    static class Writer extends Thread {
        private final int id;
        private final Random rand = new Random();

        Writer(int id) {
            this.id = id;
            setName("Writer-" + id);
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < ITERATIONS; i++) {
                    Thread.sleep(rand.nextInt(1000) + 500);

                    // ── ENTRY: acquire exclusive access ─────────────────────
                    writeLock.acquire();        // wait(writeLock) — block until free

                    // ── CRITICAL SECTION — Write ────────────────────────────
                    sharedData += 10;
                    log("WRITING  sharedData=" + sharedData + " (EXCLUSIVE — no readers allowed)");
                    Thread.sleep(rand.nextInt(500) + 200);

                    // ── EXIT: release exclusive access ──────────────────────
                    writeLock.release();        // signal(writeLock)
                    log("DONE writing → writeLock released");
                }
                log("DONE all writes.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================
    static synchronized void log(String msg) {
        System.out.printf("%-12s | wLock=%-2d mutex=%-2d readers=%-2d data=%-4d | %s%n",
                Thread.currentThread().getName(),
                writeLock.availablePermits(),
                mutex.availablePermits(),
                readerCount,
                sharedData,
                msg);
    }

    // =========================================================================
    //  MAIN
    // =========================================================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=".repeat(72));
        System.out.println("  READERS-WRITERS  (First Readers Preference)");
        System.out.println("  " + NUM_READERS + " readers, " + NUM_WRITERS
                + " writers, " + ITERATIONS + " iterations each");
        System.out.println("  Semaphores: writeLock=1  mutex=1  readerCount=0");
        System.out.println("=".repeat(72));
        System.out.printf("%-12s | %-26s | %s%n",
                "Thread", "State", "Event");
        System.out.println("-".repeat(72));

        Thread[] readers = new Thread[NUM_READERS];
        Thread[] writers = new Thread[NUM_WRITERS];
        for (int i = 0; i < NUM_READERS; i++) readers[i] = new Reader(i + 1);
        for (int i = 0; i < NUM_WRITERS; i++) writers[i] = new Writer(i + 1);

        for (Thread r : readers) r.start();
        for (Thread w : writers) w.start();
        for (Thread r : readers) r.join();
        for (Thread w : writers) w.join();

        System.out.println("-".repeat(72));
        System.out.println("All done. Final sharedData = " + sharedData
                + "  (expected: " + (NUM_WRITERS * ITERATIONS * 10) + ")");
        System.out.println("=".repeat(72));
    }
}