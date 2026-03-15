import java.util.HashMap;
import java.util.Map;

public class Warehouse {

    public static void main(String[] args) throws InterruptedException {
        int k = WarehouseConfig.NUM_TROLLEYS == -1
                ? TrolleyPool.defaultK()
                : WarehouseConfig.NUM_TROLLEYS;

        System.out.println("tick_ms=" + WarehouseConfig.TICK_MS
                + " sections=" + WarehouseConfig.SECTION_NAMES.length
                + " stockers=" + WarehouseConfig.NUM_STOCKERS
                + " pickers=" + WarehouseConfig.NUM_PICKERS
                + " section_capacity=" + WarehouseConfig.SECTION_CAPACITY
                + " trolleys=" + k);


        Map<String, Section> sectionMap = new HashMap<>();
        Section[] sectionArray = new Section[WarehouseConfig.SECTION_NAMES.length];

        for (int i = 0; i < WarehouseConfig.SECTION_NAMES.length; i++) {
            String name = WarehouseConfig.SECTION_NAMES[i];
            Section section = new Section(name, WarehouseConfig.SECTION_CAPACITY);
            section.preload(WarehouseConfig.INITIAL_BOXES_PER_SECTION);
            sectionMap.put(name, section);
            sectionArray[i] = section;
        }

        StagingArea stagingArea = new StagingArea();
        TrolleyPool trolleyPool = new TrolleyPool(k);

        Thread deliveryThread = new Thread(new DeliveryThread(stagingArea), "DEL");
        deliveryThread.setDaemon(true);
        deliveryThread.start();

        for (int i = 1; i <= WarehouseConfig.NUM_STOCKERS; i++) {
            Thread t = new Thread(new StockerThread("S" + i, stagingArea, sectionMap, trolleyPool), "S" + i);
            t.setDaemon(true);
            t.start();
        }

        for (int i = 1; i <= WarehouseConfig.NUM_PICKERS; i++) {
            long seed = WarehouseConfig.RANDOM_SEED == -1
                    ? System.nanoTime() : WarehouseConfig.RANDOM_SEED + 100 + i;
            Thread t = new Thread(new PickerThread("P" + i, sectionArray, trolleyPool, seed), "P" + i);
            t.setDaemon(true);
            t.start();
        }

        Thread.currentThread().join();
    }
}
