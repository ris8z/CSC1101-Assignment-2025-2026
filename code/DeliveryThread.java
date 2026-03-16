/*
    authors:        Cathal Dwyer, Giuseppe Esposito;
    
    stN:            22391376, 22702205;
    
    date:           15/03/2026;
    
    description:    this class is resposable for creating delivery events 

    approach:       every tick roll a dice and check if it output less then the DELIVERY_PROBABILITY 
                    if it is the case it will generate 10 random pakages as request from requriements
                    and the call staging.addDeliver(new_delivery) to actual bring the package to the staging are
                    and notify all stockers that a delivery has arrived.
*/
import java.util.Random;
import java.util.HashMap;
import java.util.Map;


class DeliveryThread implements Runnable {

    private final StagingArea stagingArea;
    private final Random rand;
    private int deliveryCount = 0;

    public DeliveryThread(StagingArea stagingArea) {
        this.stagingArea = stagingArea;
        this.rand = (WarehouseConfig.RANDOM_SEED == -1)
                ? new Random()
                : new Random(WarehouseConfig.RANDOM_SEED + 999);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            Clock.sleepTicks(1);
            if (rand.nextDouble() < WarehouseConfig.DELIVERY_PROBABILITY) {
                Map<String, Integer> delivery = generateDelivery();
                stagingArea.addDelivery(delivery);
                deliveryCount++;
                logDelivery(delivery);
            }
        }
    }

    private Map<String, Integer> generateDelivery() {
        String[] sections = WarehouseConfig.SECTION_NAMES;
        int[] counts = new int[sections.length];
        for (int i = 0; i < WarehouseConfig.BOXES_PER_DELIVERY; i++) {
            counts[rand.nextInt(sections.length)]++;
        }
        Map<String, Integer> delivery = new HashMap<>();
        for (int i = 0; i < sections.length; i++) {
            delivery.put(sections[i], counts[i]);
        }
        return delivery;
    }

    private void logDelivery(Map<String, Integer> delivery) {
        Object[] pairs = new Object[WarehouseConfig.SECTION_NAMES.length * 2 + 2];
        int idx = 0;
        pairs[idx++] = "delivery_id";
        pairs[idx++] = deliveryCount;
        for (String section : WarehouseConfig.SECTION_NAMES) {
            pairs[idx++] = section;
            pairs[idx++] = delivery.getOrDefault(section, 0);
        }
        Logger.log("delivery_arrived", pairs);
    }
}
