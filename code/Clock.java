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
