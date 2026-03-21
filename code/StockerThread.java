/*
*   authors:        Cathal Dwyer, Giuseppe Esposito;
*   
*   stN:            22391376, 22702205;
*   
*   date:           15/03/2026;
*   
*   description:    this rappresent the behaviour of the stocker thread. 
*                   The main risk to address here is the possibility of the stocker to wait
*                   infitly in a section, while all the other peakers are waiting for boxes in other
*                   sections.
*
*   approach:       To avoid this we start the stocker at the staging waiting for a load of pakages,
*                   then we move to each section (ordered by the numer of waiting pickers) in descending order,
*                   and we never wait if the section is full we go to the next one, if they are all full we go back
*                   to staging and start again.
*/
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class StockerThread implements Runnable {
    private final String id;
    private final StagingArea stagingArea;
    private final Map<String, Section> sections;
    private final TrolleyPool trolleyPool;
    private final Random rand;
    private long nextBreakAt;

    private final Map<String, Integer> currentTrolley = new HashMap<>();
    private int currentTrolleyId = -1;
    private long currentWaitStart = -1;

    public StockerThread(String id, StagingArea stagingArea, Map<String, Section> sections, TrolleyPool trolleyPool) {
        this.id = id;
        this.stagingArea = stagingArea;
        this.sections = sections;
        this.trolleyPool = trolleyPool;
        this.rand = new Random(WarehouseConfig.RANDOM_SEED + id.hashCode());
        this.nextBreakAt = scheduleNextBreak();
    }

    @Override
    public void run() {
        Thread.currentThread().setName(id);
        try {
            while (!Thread.currentThread().isInterrupted()) {
                takeBreakIfDue();
                runOneRound();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (currentTrolleyId != -1)                                 // if the thread dies we need to release the trolley
                trolleyPool.release(currentTrolleyId);
        }
    }

    // ONE ROUND LOGIC
    private void runOneRound() throws InterruptedException {
        if (currentTrolleyId == -1) {                                    // if it don't yet own a trolley
                                                                         
            if (currentWaitStart == - 1) {                               // this is for metrics of waiting time
                currentWaitStart = Clock.getTick();
            }

            stagingArea.waitForBoxes();                                  // wait for any boxes to arrived without holding any trolleys
            currentTrolleyId = aquireTrolleyOwnership();                 // then start waiting for a trolley

            currentTrolley.clear();                                      // when we got it we first clear our buffer currentTrolley
            currentTrolley.putAll(                                       // then put all the boxes inside the current trolley
                    getBoxesFromStaging(currentTrolleyId)
            );

            if(trolleyTotal(currentTrolley) == 0){                       // another stocker took all the boxes while we were waiting for the trolley
                releaseTrolleyOwnership(currentTrolleyId, 0);            // release the trolley and start again
                currentTrolleyId = -1; 
                return;                                                  
            }

            long totalWaitedTicks = Clock.getTick() - currentWaitStart;      
            logLoad(currentTrolley, currentTrolleyId, totalWaitedTicks); // we log only if we actaully get some boxes from the load and reset the waiting start tick   
            currentWaitStart = -1;                                        
        }

        String currentLocation = "staging";                              // we start at staging area
        
        for (String sectionName : prioritisedSections(currentTrolley)) { // from staging to section (and from section -> section)
            int toStock = currentTrolley.getOrDefault(sectionName, 0);
            if (toStock == 0) 
                continue;

            currentLocation = travelTo(
                    currentLocation,
                    sectionName,
                    trolleyTotal(currentTrolley),
                    currentTrolleyId
            );
            
            stockSection(sectionName, toStock, currentTrolley, currentTrolleyId);
        }

        int remaining = trolleyTotal(currentTrolley);                   
                                                                        
        if (!currentLocation.equals("staging")) {                        // back to the staging 
            travelTo(
                    currentLocation,
                    "staging",
                    remaining,
                    currentTrolleyId
            );  
        }
        
        if (remaining == 0) {                                            // We release the trolly only if there are no boxes in it
            releaseTrolleyOwnership(currentTrolleyId, 0);
            currentTrolleyId = -1; 
        }
    }

    private void stockSection(String sectionName, int toStock, Map<String, Integer> trolley, int trolleyId) throws InterruptedException {
        Section section = sections.get(sectionName);
        section.acquireStockerLock();
        
        Logger.log("stock_begin", "section", sectionName, "amount", toStock, "trolley_id", trolleyId);
        int stocked = 0;

        try {
            for (int i = 0; i < toStock; i++) {
                if (!section.addBox()) {
                    Logger.log("section_full", "section", sectionName, "waiting_for_space", false);
                    break;
                }
                Clock.sleepTicks(WarehouseConfig.STOCK_TICKS_PER_BOX);
                stocked++;
                trolley.put(sectionName, toStock - stocked);
            }
        } finally {
            section.releaseStockerLock();
        }

        Logger.log("stock_end", "section", sectionName, "stocked", stocked,
                   "remaining_load", trolleyTotal(trolley), "trolley_id", trolleyId);
    }

    private int aquireTrolleyOwnership() throws InterruptedException {
        long waitStart = Clock.getTick();
        int trolleyId = trolleyPool.acquire(true);
        long waitedTicks = Clock.getTick() - waitStart;
        Logger.log("acquire_trolley", "trolley_id", trolleyId, "waited_ticks", waitedTicks);
        return trolleyId;
    }

    public Map<String, Integer> getBoxesFromStaging(int trolleyId) throws InterruptedException {
        Map<String, Integer> load = stagingArea.takeUpToTen();           // this function will sleep the time needed
        return new HashMap<>(load);
    }

    private String travelTo(String from, String to, int load, int trolleyId) {
        Logger.log("move", "from", from, "to", to, "load", load, "trolley_id", trolleyId);
        Clock.sleepTicks(WarehouseConfig.TRAVEL_BASE_TICKS + (long) load * WarehouseConfig.TRAVEL_TICKS_PER_BOX);
        return to;
    }

    private void releaseTrolleyOwnership(int trolleyId, int remaining) {
        Logger.log("release_trolley", "trolley_id", trolleyId, "remaining_load", remaining);
        trolleyPool.release(trolleyId);
    }

    // BREAK LOGIC 
    private void takeBreakIfDue() throws InterruptedException {
        if (Clock.getTick() >= nextBreakAt) {
            Logger.log("stocker_break_start", "duration", WarehouseConfig.STOCKER_BREAK_DURATION);
            Clock.sleepTicks(WarehouseConfig.STOCKER_BREAK_DURATION);
            Logger.log("stocker_break_end");
            nextBreakAt = scheduleNextBreak();
        }
    }

    private long scheduleNextBreak() {
        int interval = WarehouseConfig.STOCKER_BREAK_INTERVAL_MIN
                + rand.nextInt(WarehouseConfig.STOCKER_BREAK_INTERVAL_MAX
                        - WarehouseConfig.STOCKER_BREAK_INTERVAL_MIN + 1);
        return Clock.getTick() + interval;
    }

    // UTILS 
    private List<String> prioritisedSections(Map<String, Integer> trolley) {
        List<String> names = new ArrayList<>();
        for (String s : WarehouseConfig.SECTION_NAMES) {
            if (trolley.getOrDefault(s, 0) > 0) names.add(s);
        }
        names.sort((a, b) -> {
            Section sa = sections.get(a);
            Section sb = sections.get(b);
            int cmp = Integer.compare(sb.getPickersWaiting(), sa.getPickersWaiting());
            if (cmp != 0) return cmp;
            return Integer.compare(sa.getBoxCount(), sb.getBoxCount());
        });
        return names;
    }

    private int trolleyTotal(Map<String, Integer> trolley) {
        return trolley.values().stream().mapToInt(Integer::intValue).sum();
    }

    private void logLoad(Map<String, Integer> load, int trolleyId, long totalWaitedTicks) {
        int numSections = WarehouseConfig.SECTION_NAMES.length;
        Object[] pairs = new Object[(numSections + 1) * 2 + 4];
        int idx = 0;
        int total = 0;
        for (String section : WarehouseConfig.SECTION_NAMES) {
            int count = load.getOrDefault(section, 0);
            pairs[idx++] = section;
            pairs[idx++] = count;
            total += count;
        }
        pairs[idx++] = "total_load";
        pairs[idx++] = total;
        pairs[idx++] = "trolley_id";
        pairs[idx++] = trolleyId;
        pairs[idx++] = "waited_ticks";
        pairs[idx]   = totalWaitedTicks;
        Logger.log("stocker_load", pairs);
    }
}
