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
        while (!Thread.currentThread().isInterrupted()) {
            try {
                takeBreakIfDue();
                runOneRound();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ONE ROUND LOGIC
    private void runOneRound() throws InterruptedException {
        String currentLocation = "staging";                                     // we start at staging are
        
        int trolleyId = aquireTrolleyOwnership();                               // aquire and load the trolley
        Map<String, Integer> trolley = loadTrolleyFromStagingArea(trolleyId);

       
        for (String sectionName : prioritisedSections(trolley)) {               // from staging to section (and from section -> section)
            int toStock = trolley.getOrDefault(sectionName, 0);
            if (toStock == 0) 
                continue;

            currentLocation = travelTo(
                    currentLocation,
                    sectionName,
                    trolleyTotal(trolley),
                    trolleyId
            );
            stockSection(sectionName, toStock, trolley, trolleyId); 
        }

        
        int remaining = trolleyTotal(trolley);                                  // back to the staging
        if (!currentLocation.equals("staging")) {
            travelTo(currentLocation, "staging", remaining, trolleyId);
        }

        releaseTrolleyOwnership(trolleyId, remaining);
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

    public Map<String, Integer> loadTrolleyFromStagingArea(int trolleyId) throws InterruptedException {
        Map<String, Integer> load = stagingArea.takeUpToTen();
        Clock.sleepTicks(WarehouseConfig.STAGING_TAKE_TICKS);
        logLoad(load, trolleyId);
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

    private void logLoad(Map<String, Integer> load, int trolleyId) {
        int numSections = WarehouseConfig.SECTION_NAMES.length;
        Object[] pairs = new Object[(numSections + 1) * 2 + 2];
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
        pairs[idx]   = trolleyId;
        Logger.log("stocker_load", pairs);
    }
}
