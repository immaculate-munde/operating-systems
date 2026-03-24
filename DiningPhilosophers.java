import java.util.concurrent.Semaphore;
import java.util.Random;

/**
 * ============================================================
 *  DINING PHILOSOPHERS PROBLEM
 *  BSc Mathematics & Computer Science — Operating Systems II
 * ============================================================
 *
 *  PROBLEM STATEMENT:
 *  ------------------
 *  5 philosophers sit at a round table. Between each adjacent
 *  pair is one fork (5 forks total). To eat, a philosopher
 *  needs BOTH their left AND right fork simultaneously.
 *  Philosophers alternate between thinking and eating.
 *
 *  THREE HAZARDS:
 *    1. Deadlock   — all pick up left fork, wait forever for right
 *    2. Starvation — a philosopher never gets both forks
 *    3. Race cond. — two philosophers grab the same fork at once
 *
 *  SOLUTION — Table semaphore (N−1 seat limit):
 *  ---------------------------------------------
 *    fork[i]  = Semaphore(1)  — one per fork, binary (free/held)
 *    table    = Semaphore(N-1) — at most N-1 philosophers seated
 *
 *  By allowing at most N-1 (4) philosophers at the table, we
 *  guarantee at least one philosopher always has both forks free.
 *  This breaks circular wait — the 4th Coffman deadlock condition.
 *
 *  EACH PHILOSOPHER:
 *    wait(table)           <- sit down (block if all 4 seats taken)
 *    wait(fork[i])         <- pick up left fork
 *    wait(fork[(i+1)%N])   <- pick up right fork
 *    eat()
 *    signal(fork[i])       <- put down left fork
 *    signal(fork[(i+1)%N]) <- put down right fork
 *    signal(table)         <- leave table
 * ============================================================
 */
public class DiningPhilosophers {

    // ── Configuration ────────────────────────────────────────────────────────
    static final int N       = 5;   // number of philosophers (and forks)
    static final int MEALS   = 3;   // how many times each philosopher eats

    // ── Semaphores ───────────────────────────────────────────────────────────

    /**
     * fork[i]: binary semaphore for fork i.
     * Initialised to 1 (fork is free on the table).
     * Philosopher i's left fork  = fork[i]
     * Philosopher i's right fork = fork[(i+1) % N]
     */
    static final Semaphore[] fork = new Semaphore[N];

    /**
     * table: counting semaphore — limits how many philosophers
     * can be seated at the table simultaneously.
     * Initialised to N-1 = 4.
     *
     * WHY N-1?
     * If all N philosophers could sit, they might all grab their
     * left fork and then wait forever for their right fork —
     * deadlock. With only N-1 seated, at least one philosopher
     * will always find both their forks free.
     */
    static final Semaphore table = new Semaphore(N - 1);

    static {
        for (int i = 0; i < N; i++)
            fork[i] = new Semaphore(1);
    }

    // =========================================================================
    //  PHILOSOPHER THREAD
    // =========================================================================
    static class Philosopher extends Thread {
        private final int id;
        private final int leftFork;   // = id
        private final int rightFork;  // = (id + 1) % N
        private final Random rand = new Random();

        Philosopher(int id) {
            this.id        = id;
            this.leftFork  = id;
            this.rightFork = (id + 1) % N;
            setName("Philosopher-" + id);
        }

        // ── Think ────────────────────────────────────────────────────────────
        void think() throws InterruptedException {
            log("is THINKING...");
            Thread.sleep(rand.nextInt(1000) + 500);
        }

        // ── Eat ──────────────────────────────────────────────────────────────
        void eat(int meal) throws InterruptedException {
            log("is EATING (meal " + meal + ")");
            Thread.sleep(rand.nextInt(800) + 400);
        }

        // ── Pick up forks ─────────────────────────────────────────────────
        void pickUpForks() throws InterruptedException {
            // ENTRY: sit at the table (block if N-1 already seated)
            table.acquire();
            log("seated  | table semaphore now = " + table.availablePermits());

            // Acquire left fork
            fork[leftFork].acquire();
            log("got     fork " + leftFork + " (left)");

            // Acquire right fork
            fork[rightFork].acquire();
            log("got     fork " + rightFork + " (right) → ready to eat");
        }

        // ── Put down forks ────────────────────────────────────────────────
        void putDownForks() {
            // Release right fork first, then left (order doesn't matter here)
            fork[rightFork].release();
            fork[leftFork].release();
            log("put down fork " + leftFork + " + fork " + rightFork);

            // EXIT: leave the table
            table.release();
            log("left table | table semaphore now = " + table.availablePermits());
        }

        @Override
        public void run() {
            try {
                for (int meal = 1; meal <= MEALS; meal++) {
                    think();
                    pickUpForks();
                    eat(meal);
                    putDownForks();
                }
                log("DONE — had all " + MEALS + " meals.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================

    /** Formatted log: thread name | fork states | message */
    static synchronized void log(String message) {
        // Build a compact fork state: [1 0 1 1 0] means fork 1,3 free; 0,2,4 held
        StringBuilder fs = new StringBuilder("forks[");
        for (int i = 0; i < N; i++)
            fs.append(fork[i].availablePermits()).append(i < N-1 ? " " : "");
        fs.append("]");

        System.out.printf("%-15s | %s | %s%n",
                Thread.currentThread().getName(), fs, message);
    }

    // =========================================================================
    //  MAIN
    // =========================================================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=".repeat(68));
        System.out.println("  DINING PHILOSOPHERS  |  N=" + N
                + "  |  " + MEALS + " meals each");
        System.out.println("  Semaphores: fork[0..4]=1  table=" + (N-1));
        System.out.println("  fork[i]=1 means free, 0 means held");
        System.out.println("=".repeat(68));
        System.out.printf("%-15s | %-14s | %s%n",
                "Thread", "Fork states", "Event");
        System.out.println("-".repeat(68));

        Thread[] philosophers = new Thread[N];
        for (int i = 0; i < N; i++)
            philosophers[i] = new Philosopher(i);
        for (Thread p : philosophers) p.start();
        for (Thread p : philosophers) p.join();

        System.out.println("-".repeat(68));
        System.out.println("All philosophers finished dining.");
        System.out.println("=".repeat(68));
    }
}