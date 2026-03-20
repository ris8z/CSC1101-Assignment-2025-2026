/*
    authors:        Cathal Dwyer, Giuseppe Esposito;
    
    stN:            22391376, 22702205;
    
    date:           15/03/2026;
    
    description:   this represent the Picker thread behaviour 
                        - sleep if needed to get to the right number of picks event x day
                        - acquire trolley ownership
                        - pick a random section
                        - wait until you can get one boxes
                        - release the trolley ownership


    approach:      here the logic is pretty straight forward the tricky part is having the right number of pick event 
                   per day, because just emmiting it one every MEAN_SLEEP_TICKS (defined as in the docs) it's not enough
                   because the work of picker may waste a bit of time (i.e. waiting for the trolley) and the the overall
                   delta T between event T1 and event T2 becomes MEAN_SLEEP_TICKS + (time needed to complete the task),
                   To avoid this we record a lastTickWeWorked that we use to understand when we are supposed to start a new event,
                   if that is in the past we don't wait at all, if it is in the future we wait for that time.   
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

    private long lastTickWeWorked;

    public PickerThread(String id, Section[] sectionArray, TrolleyPool trolleyPool, long randomSeed) {
        this.id = id;
        this.sectionArray = sectionArray;
        this.trolleyPool = trolleyPool;
        this.rand = new Random(randomSeed);
        this.lastTickWeWorked = Clock.getTick();
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
        long coolDown = (long) (2.0 * MEAN_SLEEP_TICKS * rand.nextDouble());
        long weShouldWorkAgain = lastTickWeWorked + coolDown;
        long ticksToSleep = weShouldWorkAgain - Clock.getTick();

        if (ticksToSleep > 0)                                           // if weShouldWorkAgain is in the future we wait 
            Clock.sleepTicks(ticksToSleep);                          
                                                                    
        lastTickWeWorked = Clock.getTick();                             // if weShouldWorkAgain is in the past we skip the waiting 
                                                                  

        
        long waitStart = Clock.getTick();                               // Acquire trolley before attempting pick
        int trolleyId = trolleyPool.acquire(false);
        long waitedTicks = Clock.getTick() - waitStart;
        Logger.log( "acquire_trolley", "trolley_id", trolleyId, "waited_ticks", waitedTicks);

        try{
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

            Clock.sleepTicks(WarehouseConfig.PICK_TICKS);               // note that the sleep is itended put outside of the takeBox() function
                                                                        // to allow multiple pickers to pick together
            Logger.log(
                    "pick_done",
                    "pick_id", pickId,
                    "section", section.getName(),
                    "waited_ticks", pickedWaitedTicks,
                    "trolley_id", trolleyId
            );
        } finally {
            Logger.log(
                    "release_trolley",
                    "trolley_id", trolleyId, 
                    "remaining_load", 0
            );
            trolleyPool.release(trolleyId);                             // Trolley now empty, release it
        }
    }
}
