import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

/**
 * ============================================================
 *  SLEEPING BARBER PROBLEM
 *  BSc Mathematics & Computer Science — Operating Systems II
 * ============================================================
 *
 *  PROBLEM STATEMENT:
 *  ------------------
 *  A barbershop has:
 *    - 1 barber with 1 barber chair
 *    - A waiting room with N seats
 *
 *  Rules:
 *    - If NO customers: barber sleeps in the barber chair
 *    - If barber is SLEEPING: arriving customer wakes the barber
 *    - If barber is BUSY: arriving customer sits in waiting room
 *    - If waiting room is FULL: arriving customer leaves (balks)
 *
 *  HAZARDS:
 *    1. Lost wakeup  — customer arrives just as barber checks;
 *                      neither wakes the other
 *    2. Race condition — two customers read waiting count
 *                        simultaneously, both sit, overflowing
 *
 *  SOLUTION — Three Semaphores:
 *  ----------------------------
 *    customers = 0   Barber does wait(customers) to sleep.
 *                    Customer does signal(customers) to wake barber.
 *
 *    barber    = 0   Customer does wait(barber) to wait for chair.
 *                    Barber does signal(barber) when chair is free.
 *
 *    mutex     = 1   Protects waitingCount — ensures the
 *                    check-and-increment is atomic.
 *
 *  KEY INSIGHT:
 *    The mutex wraps the entire "check waitingCount → sit/leave"
 *    decision atomically. Without it, two customers could both
 *    read waitingCount < N and both sit, overflowing the room.
 * ============================================================
 */
public class SleepingBarber {

    // ── Configuration ────────────────────────────────────────────────────────
    static final int WAITING_ROOM_SIZE = 5;   // N: number of waiting seats
    static final int NUM_CUSTOMERS     = 12;  // total customers to simulate

    // ── Semaphores ───────────────────────────────────────────────────────────

    /**
     * customers: counting semaphore — tracks customers waiting for the barber.
     * Init = 0 (barber starts asleep — no one waiting).
     * Customer does signal(customers) when they sit down.
     * Barber does wait(customers)   to sleep until someone arrives.
     */
    static final Semaphore customers = new Semaphore(0);

    /**
     * barber: binary semaphore — signals a waiting customer that the
     * barber chair is ready.
     * Init = 0 (chair not ready yet).
     * Barber does signal(barber) when ready to cut.
     * Customer does wait(barber)  to wait for their turn in the chair.
     */
    static final Semaphore barber = new Semaphore(0);

    /**
     * mutex: binary semaphore — protects waitingCount.
     * Init = 1 (unlocked).
     * Both barber and customer acquire this before reading/writing waitingCount.
     */
    static final Semaphore mutex = new Semaphore(1);

    // ── Shared state ─────────────────────────────────────────────────────────
    static int waitingCount = 0;   // how many customers are currently waiting

    // =========================================================================
    //  BARBER THREAD
    // =========================================================================
    static class Barber extends Thread {
        Barber() { setName("Barber"); }

        @Override
        public void run() {
            log("Shop open. Going to sleep...");
            try {
                while (true) {

                    // ── SLEEP until a customer arrives ──────────────────────
                    // wait(customers): block here with value 0 (sleeping)
                    customers.acquire();     // woken by customer signal

                    // ── Reduce waiting count atomically ─────────────────────
                    mutex.acquire();
                    waitingCount--;
                    log("Customer leaves waiting room → chair. Waiting: " + waitingCount);

                    // ── Signal the customer: chair is ready ─────────────────
                    barber.release();        // signal(barber)
                    mutex.release();

                    // ── Cut hair (outside critical section) ─────────────────
                    cutHair();
                }
            } catch (InterruptedException e) {
                log("Closing up shop.");
                Thread.currentThread().interrupt();
            }
        }

        void cutHair() throws InterruptedException {
            log("Cutting hair...");
            Thread.sleep(new Random().nextInt(1200) + 600);
            log("Done cutting. Ready for next customer.");
        }
    }

    // =========================================================================
    //  CUSTOMER THREAD
    // =========================================================================
    static class Customer extends Thread {
        private final int id;

        Customer(int id) {
            this.id = id;
            setName("Customer-" + id);
        }

        @Override
        public void run() {
            try {
                // ── ENTRY: lock and check waiting room ──────────────────────
                mutex.acquire();                    // wait(mutex)

                if (waitingCount < WAITING_ROOM_SIZE) {
                    // There is a free seat — sit down
                    waitingCount++;
                    log("Sat in waiting room. Waiting: " + waitingCount + "/" + WAITING_ROOM_SIZE);

                    // Wake the barber (or signal there's one more customer)
                    customers.release();            // signal(customers)
                    mutex.release();                // signal(mutex)

                    // ── Wait for barber to call us to the chair ─────────────
                    barber.acquire();               // wait(barber)
                    log("In the barber chair — getting haircut!");

                } else {
                    // No seats — leave without a haircut
                    log("Waiting room full (" + waitingCount + "/" + WAITING_ROOM_SIZE + ") → LEAVING.");
                    mutex.release();               // signal(mutex)
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================
    static synchronized void log(String msg) {
        System.out.printf("%-14s | customers=%-2d barber=%-2d mutex=%-1d waiting=%-2d | %s%n",
                Thread.currentThread().getName(),
                customers.availablePermits(),
                barber.availablePermits(),
                mutex.availablePermits(),
                waitingCount,
                msg);
    }

    // =========================================================================
    //  MAIN
    // =========================================================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=".repeat(72));
        System.out.println("  SLEEPING BARBER  |  Waiting room N=" + WAITING_ROOM_SIZE
                + "  |  " + NUM_CUSTOMERS + " customers");
        System.out.println("  Semaphores: customers=0  barber=0  mutex=1");
        System.out.println("=".repeat(72));
        System.out.printf("%-14s | %-28s | %s%n",
                "Thread", "Semaphore state", "Event");
        System.out.println("-".repeat(72));

        // Start the barber as a daemon — it runs until all customers are done
        Barber b = new Barber();
        b.setDaemon(true);
        b.start();

        Random rand = new Random();
        for (int i = 1; i <= NUM_CUSTOMERS; i++) {
            new Customer(i).start();
            Thread.sleep(rand.nextInt(500) + 100);  // customers arrive at random intervals
        }

        // Give the barber enough time to finish the last haircut
        Thread.sleep(6000);
        System.out.println("-".repeat(72));
        System.out.println("Simulation complete.");
        System.out.println("=".repeat(72));
    }
}