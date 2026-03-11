public class Clock implements Runnable {

    private static final Clock INSTANCE = new Clock();
    private Clock() {}
    public static Clock getInstance() { return INSTANCE; }

    private volatile long tick = 0;

    public void start() {
        Thread t = new Thread(this, "Clock");
        t.setDaemon(true);
        t.start();
    }

    public static long getTick() {
        return INSTANCE.tick;
    }

    // SUGGESTION: we should use and observer pattern (clock ground truth that notify when the time it's passed for all the others)
    // because now we'are using a sleep for the cloack
    // and a different sleep when we want to put a thread on aside,
    // the result of 3 sleep of 10 sec could not be the same of 1 sleep of 30 sec
    // bc the os taks a bit of time to wake up thread
    public static void sleepTicks(long ticks) {
        if (ticks <= 0) return;
        try {
            Thread.sleep(ticks * WarehouseConfig.TICK_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(WarehouseConfig.TICK_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            tick++;
        }
    }
}
