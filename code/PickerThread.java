/*
    authors:        Cathal Dwyer, Giuseppe Esposito;
    
    stN:            22391376, 22702205;
    
    date:           15/03/2026;
    
    description:   this rappresent the Picker thread behaviour 
                        - sleep if needed to get to the right number of picks event x day
                        - aquire trolley ownership
                        - pick a random section
                        - wait until you can get one boxes
                        - release the trolley ownership


    approach:      here the logic is pretty straight forward the tricky part is having the right number of pick event 
                   per day, because just emmiting it one every MEAN_SLEEP_TICKS (difiend as in the docs) it's not enough
                   because the work of picker may waste a bit of time (i.e. waiting for the trolley) and the the overall
                   delta T between event T1 and event T2 bacome MEAN_SLEEP_TICKS + (time needed to complete the task),
                   To avoid this we record a baseLineTick that rember use when we were supposted to start a new event
                   and if it is smaller the the current tick means we need to skip sleep and work instead to recover.
                    
*/
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class PickerThread implements Runnable {

    private final String id;
    private final Section[] sectionArray;
    private final TrolleyPool trolleyPool;
    private final Random rand;

    private static final AtomicInteger pickIdCounter = new AtomicInteger(1);

    private static final double MEAN_SLEEP_TICKS =
            (double) WarehouseConfig.TICKS_PER_DAY
                    * WarehouseConfig.NUM_PICKERS
                    / WarehouseConfig.TARGET_PICKS_PER_DAY;

    private long baseLineTick;

    public PickerThread(String id, Section[] sectionArray, TrolleyPool trolleyPool, long randomSeed) {
        this.id = id;
        this.sectionArray = sectionArray;
        this.trolleyPool = trolleyPool;
        this.rand = new Random(randomSeed);
        this.baseLineTick = Clock.getTick();
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
        long randomInterval = (long) (2.0 * MEAN_SLEEP_TICKS * rand.nextDouble());

        baseLineTick += randomInterval;

        long currentTick = Clock.getTick();
        long ticksToSleep = baseLineTick - currentTick;

        if (ticksToSleep > 0) {                 // if we are early we sleep
            Clock.sleepTicks(ticksToSleep);
        } else {                                // if we are late we skip sleep and go working
            baseLineTick = currentTick;         // and we reset the base line at the current time (if we are very late just pretend we start now)
        }                                       // because if we are very late probaly it's bacuse there were no packages at all for some time.

        
        long waitStart = Clock.getTick();       // Acquire trolley before attempting pick
        int trolleyId = trolleyPool.acquire(false);
        long waitedTicks = Clock.getTick() - waitStart;
        Logger.log(
                "acquire_trolley", 
                "trolley_id", trolleyId, 
                "waited_ticks", waitedTicks
        );

        Section section = sectionArray[rand.nextInt(sectionArray.length)];
        int pickId = pickIdCounter.getAndIncrement();

        Logger.log(
                "pick_start", 
                "pick_id", pickId, 
                "section", section.getName(), 
                "trolley_id", trolleyId
        );

        long pickWaitStart = Clock.getTick();
        String box = section.takeBox();
        long pickedWaitedTicks = Clock.getTick() - pickWaitStart;

        Clock.sleepTicks(WarehouseConfig.PICK_TICKS);

        Logger.log(
                "pick_done",
                "pick_id", pickId,
                "section", section.getName(),
                "waited_ticks", pickedWaitedTicks,
                "trolley_id", trolleyId
        );

        Logger.log(
                "release_trolley",
                "trolley_id", trolleyId, 
                "remaining_load", 0
        );
        trolleyPool.release(trolleyId);         // Trolley now empty, release it
    }
}
