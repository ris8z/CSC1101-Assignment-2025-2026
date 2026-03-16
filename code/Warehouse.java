/*
    authors:        Cathal Dwyer, Giuseppe Esposito;
    
    stN:            22391376, 22702205;
    
    date:           15/03/2026;
    
    description:    This class is the orchestrator of the warehouse simulation. 
                    It is responsible to:
                        - bootstrap the enviroment (read value from config)
                        - init all shared resources (Sections, stagin area, trolley poool)
                        - spawn the concurrent actors (Delivery, Stockers, Pickers)

    approach:       1. Config: it reads config parameters from WarehouseConfig to
                        determinte (number of sections, K trolleys, etc.)

                    2. Init shared resources: init section objs (preload them with inital boxes),
                        init stagingArea and trolleyPoll

                    3. Thread spawining: creates and start threads for (delivery, stocker and pickers)
                        passing them the shared resource objs

                    4. Time of execution: threads are set as demoans (demons -> do not prevent JVM to close)
                        and the mian thread calls Thread.currentThread().join() to wait infitily,
                        ergo to stop the simulation CNT-C is needed.
*/
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
