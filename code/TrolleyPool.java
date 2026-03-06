import java.util.concurrent.ArrayBlockingQueue;

public class TrolleyPool {

    private final ArrayBlockingQueue<Integer> available;

    public TrolleyPool(int k) {
        available = new ArrayBlockingQueue<>(k);
        for (int i = 1; i <= k; i++) available.add(i);
    }

    // Blocks until a trolley is free, returns trolley id
    public int acquire() throws InterruptedException {
        return available.take();
    }

    public void release(int trolleyId) {
        available.add(trolleyId);
    }

    public static int defaultK() {
        return (WarehouseConfig.NUM_STOCKERS + WarehouseConfig.NUM_PICKERS) / 2;
    }
}