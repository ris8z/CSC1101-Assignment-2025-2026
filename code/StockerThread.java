import java.util.HashMap;
import java.util.Map;

public class StockerThread implements Runnable {
    private final String id;
    private final StagingArea stagingArea;
    private final Map<String, Section> sections;
    private final TrolleyPool trolleyPool;

    public StockerThread(String id, StagingArea stagingArea, Map<String, Section> sections, TrolleyPool trolleyPool) {
        this.id = id;
        this.stagingArea = stagingArea;
        this.sections = sections;
        this.trolleyPool = trolleyPool;
    }

    @Override
    public void run() {
        Thread.currentThread().setName(id);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                runOneRound();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void runOneRound() throws InterruptedException {
        // Acquire trolley before taking from staging
        long waitStart = Clock.getTick();
        int trolleyId = trolleyPool.acquire();
        long waitedTicks = Clock.getTick() - waitStart;
        Logger.log("acquire_trolley", "trolley_id", trolleyId, "waited_ticks", waitedTicks);

        Map<String, Integer> load = stagingArea.takeAll();
        Clock.sleepTicks(WarehouseConfig.STAGING_TAKE_TICKS);
        logLoad(load, trolleyId);

        Map<String, Integer> trolley = new HashMap<>(load);
        String currentLocation = "staging";

        for (String sectionName : WarehouseConfig.SECTION_NAMES) {
            int toStock = trolley.getOrDefault(sectionName, 0);
            if (toStock == 0) continue;

            Section section = sections.get(sectionName);

            int totalLoad = trolleyTotal(trolley);
            logMove(currentLocation, sectionName, totalLoad, trolleyId);
            Clock.sleepTicks(WarehouseConfig.TRAVEL_BASE_TICKS
                    + (long) totalLoad * WarehouseConfig.TRAVEL_TICKS_PER_BOX);
            currentLocation = sectionName;

            section.acquireStockerLock();
            Logger.log("stock_begin", "section", sectionName, "amount", toStock, "trolley_id", trolleyId);
            int stocked = 0;
            try {
                for (int i = 0; i < toStock; i++) {
                    while (!section.addBox(sectionName)) {
                        Logger.log("section_full", "section", sectionName, "waiting_for_space", true);
                        section.releaseStockerLock();
                        section.waitForSpace();
                        section.acquireStockerLock();
                    }
                    Clock.sleepTicks(WarehouseConfig.STOCK_TICKS_PER_BOX);
                    stocked++;
                    trolley.put(sectionName, toStock - stocked);
                }
                trolley.put(sectionName, 0);
            } finally {
                section.releaseStockerLock();
            }
            Logger.log("stock_end", "section", sectionName, "stocked", stocked,
                    "remaining_load", trolleyTotal(trolley), "trolley_id", trolleyId);
        }

        int remaining = trolleyTotal(trolley);
        logMove(currentLocation, "staging", remaining, trolleyId);
        Clock.sleepTicks(WarehouseConfig.TRAVEL_BASE_TICKS
                + (long) remaining * WarehouseConfig.TRAVEL_TICKS_PER_BOX);

        // Trolley must be empty before release
        Logger.log("release_trolley", "trolley_id", trolleyId, "remaining_load", remaining);
        trolleyPool.release(trolleyId);
    }

    private int trolleyTotal(Map<String, Integer> trolley) {
        return trolley.values().stream().mapToInt(Integer::intValue).sum();
    }

    private void logLoad(Map<String, Integer> load, int trolleyId)
    {
    int numSections = WarehouseConfig.SECTION_NAMES.length;
    Object[] pairs = new Object[(numSections + 1) * 2 + 2];
    int idx = 0;
    int total = 0;
    for (String section : WarehouseConfig.SECTION_NAMES)
    {
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

    private void logMove(String from, String to, int load, int trolleyId) {
        Logger.log("move", "from", from, "to", to, "load", load, "trolley_id", trolleyId);
    }
}