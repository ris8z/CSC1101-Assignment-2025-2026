import java.util.HashMap;
import java.util.Map;

public class Warehouse {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("tick_ms=" + WarehouseConfig.TICK_MS
                + " sections=" + WarehouseConfig.SECTION_NAMES.length
                + " stockers=" + WarehouseConfig.NUM_STOCKERS
                + " pickers=" + WarehouseConfig.NUM_PICKERS);

        Clock.getInstance().start();

        Map<String, Section> sectionMap = new HashMap<>();
        Section[] sectionArray = new Section[WarehouseConfig.SECTION_NAMES.length];

        for (int i = 0; i < WarehouseConfig.SECTION_NAMES.length; i++) {
            String name = WarehouseConfig.SECTION_NAMES[i];
            Section section = new Section(name);
            section.preload(WarehouseConfig.INITIAL_BOXES_PER_SECTION);
            sectionMap.put(name, section);
            sectionArray[i] = section;
        }

        StagingArea stagingArea = new StagingArea();

        Thread deliveryThread = new Thread(new DeliveryThread(stagingArea), "DEL");
        deliveryThread.setDaemon(true);
        deliveryThread.start();

        for (int i = 1; i <= WarehouseConfig.NUM_STOCKERS; i++) {
            long seed = (WarehouseConfig.RANDOM_SEED == -1)
                    ? System.nanoTime() : WarehouseConfig.RANDOM_SEED + i;
            Thread t = new Thread(new StockerThread("S" + i, stagingArea, sectionMap), "S" + i);
            t.setDaemon(true);
            t.start();
        }

        for (int i = 1; i <= WarehouseConfig.NUM_PICKERS; i++) {
            long seed = (WarehouseConfig.RANDOM_SEED == -1)
                    ? System.nanoTime() : WarehouseConfig.RANDOM_SEED + 100 + i;
            Thread t = new Thread(new PickerThread("P" + i, sectionArray, seed), "P" + i);
            t.setDaemon(true);
            t.start();
        }

        Thread.currentThread().join();
    }
}