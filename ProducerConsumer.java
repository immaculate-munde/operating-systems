import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.Semaphore;

/**
 * ============================================================
 *  PRODUCER-CONSUMER PROBLEM (Bounded Buffer)
 *  BSc Mathematics & Computer Science — Operating Systems II
 * ============================================================
 *
 *  PROBLEM STATEMENT:
 *  ------------------
 *  Producers generate items and place them into a shared buffer.
 *  Consumers remove items from the buffer and process them.
 *  The buffer has a fixed capacity N.
 *
 *  THREE RULES to enforce:
 *    1. A producer must WAIT if the buffer is FULL
 *    2. A consumer must WAIT if the buffer is EMPTY
 *    3. Only ONE process may access the buffer at a time (mutual exclusion)
 *
 *  SOLUTION — Three Semaphores:
 *  ----------------------------
 *    empty  = N  (counts free slots;   producer does wait(empty),  consumer does signal(empty))
 *    full   = 0  (counts filled slots; consumer does wait(full),   producer does signal(full))
 *    mutex  = 1  (binary lock;         both sides do wait/signal around the critical section)
 *
 *  CRITICAL ORDER (must never be swapped):
 *    Producer:  wait(empty) -> wait(mutex) -> [write] -> signal(mutex) -> signal(full)
 *    Consumer:  wait(full)  -> wait(mutex) -> [read]  -> signal(mutex) -> signal(empty)
 *
 *  Swapping wait(mutex) before wait(empty/full) would cause DEADLOCK.
 * ============================================================
 */
public class ProducerConsumer {

    // -- Configuration -------------------------------------------------------
    static final int BUFFER_CAPACITY = 5;   // N: size of the bounded buffer
    static final int NUM_PRODUCERS   = 2;   // number of producer threads
    static final int NUM_CONSUMERS   = 2;   // number of consumer threads
    static final int ITEMS_PER_ACTOR = 5;   // how many items each producer/consumer handles

    // -- Shared Buffer -------------------------------------------------------
    static final Queue<Integer> buffer = new LinkedList<>();

    // -- Semaphores ----------------------------------------------------------

    /**
     * empty: counting semaphore — tracks the number of FREE slots in the buffer.
     * Initialised to BUFFER_CAPACITY (all slots free at the start).
     * Producer calls wait(empty)   before writing -> one less free slot.
     * Consumer calls signal(empty) after  reading -> one more free slot.
     */
    static final Semaphore empty = new Semaphore(BUFFER_CAPACITY);

    /**
     * full: counting semaphore — tracks the number of FILLED slots in the buffer.
     * Initialised to 0 (nothing in the buffer yet).
     * Producer calls signal(full) after  writing -> one more item is available.
     * Consumer calls wait(full)   before reading -> one less item available.
     */
    static final Semaphore full = new Semaphore(0);

    /**
     * mutex: binary semaphore — enforces MUTUAL EXCLUSION on the buffer.
     * Initialised to 1 (unlocked).
     * Both sides call wait(mutex) before touching the buffer,
     * and signal(mutex) after leaving, so only ONE thread is inside at a time.
     */
    static final Semaphore mutex = new Semaphore(1);

    // -- Thread-safe item ID counter -----------------------------------------
    static int globalItemId = 0;

    static synchronized int nextItemId() {
        return ++globalItemId;
    }

    // =========================================================================
    //  PRODUCER THREAD
    // =========================================================================
    static class Producer extends Thread {
        private final int id;
        private final Random rand = new Random();

        Producer(int id) {
            this.id = id;
            setName("Producer-" + id);
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < ITEMS_PER_ACTOR; i++) {

                    // --- Produce an item OUTSIDE the critical section -----------
                    int item = nextItemId();
                    Thread.sleep(rand.nextInt(600) + 200);   // simulate production time
                    log("produced item [" + item + "]");

                    // --- ENTRY SECTION ------------------------------------------
                    // wait(empty): block until at least one slot is free
                    empty.acquire();          // empty = empty - 1

                    // wait(mutex): lock the buffer for exclusive access
                    mutex.acquire();          // mutex = 0 (locked)

                    // --- CRITICAL SECTION: write to buffer ----------------------
                    buffer.add(item);
                    log("wrote    [" + item + "] -> buffer " + bufferState());

                    // --- EXIT SECTION -------------------------------------------
                    // signal(mutex): unlock the buffer
                    mutex.release();          // mutex = 1 (unlocked)

                    // signal(full): tell consumers one more item is ready
                    full.release();           // full = full + 1
                }

                log("DONE producing.");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // =========================================================================
    //  CONSUMER THREAD
    // =========================================================================
    static class Consumer extends Thread {
        private final int id;
        private final Random rand = new Random();

        Consumer(int id) {
            this.id = id;
            setName("Consumer-" + id);
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < ITEMS_PER_ACTOR; i++) {

                    // --- ENTRY SECTION ------------------------------------------
                    // wait(full): block until at least one item is available
                    full.acquire();           // full = full - 1

                    // wait(mutex): lock the buffer for exclusive access
                    mutex.acquire();          // mutex = 0 (locked)

                    // --- CRITICAL SECTION: read from buffer ---------------------
                    int item = buffer.poll();
                    log("read     [" + item + "] <- buffer " + bufferState());

                    // --- EXIT SECTION -------------------------------------------
                    // signal(mutex): unlock the buffer
                    mutex.release();          // mutex = 1 (unlocked)

                    // signal(empty): tell producers one slot is now free
                    empty.release();          // empty = empty + 1

                    // --- Process the item OUTSIDE the critical section ----------
                    Thread.sleep(rand.nextInt(800) + 300);   // simulate processing time
                    log("consumed [" + item + "]");
                }

                log("DONE consuming.");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // =========================================================================
    //  UTILITY METHODS
    // =========================================================================

    /** Visual snapshot of the buffer: filled items + empty dots. */
    static synchronized String bufferState() {
        StringBuilder sb = new StringBuilder("[");
        for (int val : buffer) {
            sb.append(String.format("%2d", val)).append(" ");
        }
        int emptySlots = BUFFER_CAPACITY - buffer.size();
        for (int i = 0; i < emptySlots; i++) sb.append(" . ");
        sb.append("] ");
        sb.append(buffer.size()).append("/").append(BUFFER_CAPACITY);
        return sb.toString();
    }

    /** Formatted log line: thread name | semaphore values | event message. */
    static synchronized void log(String message) {
        System.out.printf("%-14s | empty=%-2d full=%-2d mutex=%d | %s%n",
                Thread.currentThread().getName(),
                empty.availablePermits(),
                full.availablePermits(),
                mutex.availablePermits(),
                message);
    }

    // =========================================================================
    //  MAIN
    // =========================================================================
    public static void main(String[] args) throws InterruptedException {

        System.out.println("=".repeat(70));
        System.out.println("  PRODUCER-CONSUMER  |  N=" + BUFFER_CAPACITY
                + "  |  " + NUM_PRODUCERS + " producers  " + NUM_CONSUMERS + " consumers"
                + "  |  " + ITEMS_PER_ACTOR + " items each");
        System.out.println("  Semaphores: empty=" + BUFFER_CAPACITY
                + "  full=0  mutex=1");
        System.out.println("=".repeat(70));
        System.out.printf("%-14s | %-22s | %s%n",
                "Thread", "Semaphore values", "Event");
        System.out.println("-".repeat(70));

        // Create threads
        Thread[] producers = new Thread[NUM_PRODUCERS];
        Thread[] consumers = new Thread[NUM_CONSUMERS];
        for (int i = 0; i < NUM_PRODUCERS; i++) producers[i] = new Producer(i + 1);
        for (int i = 0; i < NUM_CONSUMERS; i++) consumers[i] = new Consumer(i + 1);

        // Start consumers first so they are ready to receive
        for (Thread c : consumers) c.start();
        for (Thread p : producers) p.start();

        // Wait for all to finish
        for (Thread p : producers) p.join();
        for (Thread c : consumers) c.join();

        System.out.println("-".repeat(70));
        System.out.println("All threads finished.");
        System.out.println("Final buffer size: " + buffer.size() + "  (expected: 0)");
        System.out.println("=".repeat(70));
    }
}