import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class PickerThread implements Runnable {

    private final String id;
    private final Section[] sectionArray;
    private final TrolleyPool trolleyPool;
    private final Random rand;

    private static final AtomicInteger pickIdCounter = new AtomicInteger(1);

    // Each picker sleeps avg (TICKS_PER_DAY * NUM_PICKERS / TARGET_PICKS_PER_DAY) ticks
    // so combined they hit TARGET_PICKS_PER_DAY attempts per day
    private static final double MEAN_SLEEP_TICKS =
            (double) WarehouseConfig.TICKS_PER_DAY
                    * WarehouseConfig.NUM_PICKERS
                    / WarehouseConfig.TARGET_PICKS_PER_DAY;

    public PickerThread(String id, Section[] sectionArray, TrolleyPool trolleyPool, long randomSeed) {
        this.id = id;
        this.sectionArray = sectionArray;
        this.trolleyPool = trolleyPool;
        this.rand = new Random(randomSeed);
    }

    @Override
    public void run() {
        Thread.currentThread().setName(id);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                runOnePickAttempt();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void runOnePickAttempt() throws InterruptedException {
        Clock.sleepTicks((long) (2.0 * MEAN_SLEEP_TICKS * rand.nextDouble()));

        // Acquire trolley before attempting pick
        long waitStart = Clock.getTick();
        int trolleyId = trolleyPool.acquire();
        long waitedTicks = Clock.getTick() - waitStart;
        Logger.log("acquire_trolley", "trolley_id", trolleyId, "waited_ticks", waitedTicks);

        Section section = sectionArray[rand.nextInt(sectionArray.length)];
        int pickId = pickIdCounter.getAndIncrement();

        Logger.log("pick_start", "pick_id", pickId, "section", section.getName(), "trolley_id", trolleyId);

        long pickWaitStart = Clock.getTick();
        String box = section.takeBox();
        long pickedWaitedTicks = Clock.getTick() - pickWaitStart;

        Clock.sleepTicks(WarehouseConfig.PICK_TICKS);

        Logger.log("pick_done", "pick_id", pickId, "section", section.getName(),
                "waited_ticks", pickedWaitedTicks, "trolley_id", trolleyId);

        // Trolley now empty, release it
        Logger.log("release_trolley", "trolley_id", trolleyId, "remaining_load", 0);
        trolleyPool.release(trolleyId);
    }
}