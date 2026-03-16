/*
    authors:        Cathal Dwyer, Giuseppe Esposito;
    
    stN:            22391376, 22702205;
    
    date:           15/03/2026;
    

    description:    this rappresent the Staging Area that is a "resource" used by 2 actors:
                        - stocker 
                        - delivery 

                    By requirments only 1 stocker can interact with this area at time.

                    But nothing is said about stocker + delivery interaction with this area
                    (so we will assume that can be done at same time)

                    the class exposes 3 methods:
                       1. addDelivery  (used by DeliveryThread to add 10 packages to the area, and notify stockers of the delivery)
                       2. waitForBoxes (used by the StokerThread to wait for the delivery to bring some boxes)
                       3. takeUpToTen  (used by the StokerThread to try and pick 10 packages from the area, and lock the area from other stockers)


    approach:       We need to use 2 different locks 
                        1. to ensure that only 1 stocker is interacting (stockerLock)
                            - here we don't need a condition (interal waiting room) becauser once you aquire the lock you can do your work (picke the pagakges)
                            - to avoid starvation we use fair=true that ensure a FIFO queue

                        2. to allow the stockers to wait for a delivery and the delivery to wake them up only we it brings package
                            - here we need a condtion (watiting room) because when you aquire the lock and there is no boxes you have to wait
                            into an internal room.
*/
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;


public class StagingArea {
    private final LinkedBlockingQueue<String> boxQueue;     // Thread-safe DS so stocker and delivery can work together
    private final ReentrantLock stockerLock;                // lock to ensure just one stocker at time is working here
                                                            
    private final ReentrantLock deliveryLock;
    private final Condition deliveryWaitingRoom;

    public StagingArea(){
        this.boxQueue = new LinkedBlockingQueue<>();
        this.stockerLock = new ReentrantLock(true);
        this.deliveryLock = new ReentrantLock();
        this.deliveryWaitingRoom = deliveryLock.newCondition();
    }

    public void addDelivery(Map<String, Integer> delivery) {
        delivery.forEach((section, count) -> boxQueue.addAll(Collections.nCopies(count, section)));

        deliveryLock.lock();                                // Wake up waiting stockers
        try {
            deliveryWaitingRoom.signalAll();
        } finally {
            deliveryLock.unlock();
        }
    }

    public void waitForBoxes() throws InterruptedException {
        deliveryLock.lockInterruptibly();
        try {
            while (boxQueue.isEmpty()) {
                deliveryWaitingRoom.await();
            }
        } finally {
            deliveryLock.unlock();
        } 
    }

    public Map<String, Integer> takeUpToTen() throws InterruptedException {
        stockerLock.lockInterruptibly();                    // only 1 stocker allowed
        try {
            Map<String, Integer> load = new HashMap<>();
            
            String firstBox = boxQueue.poll();              
            if (firstBox == null) {                         // while we were loading the section got emptyed just return an empty cart
                return load;                                // to the stocker
            }
            load.put(firstBox, 1);
            
            for (int i = 1; i < 10; i++) {                  // try to grab 9 more if they are there
                String box = boxQueue.poll();
                if (box == null)                            // the queue is empty quit
                    break;
                load.merge(box, 1, Integer::sum);
            }
            
            Clock.sleepTicks(
                    WarehouseConfig.STAGING_TAKE_TICKS       // taking from the stage takes always the same time regaredeless of the number of boxes
            ); 

            return load;
        } finally {
            stockerLock.unlock();                           // let the next stocker in 
        }
    }
}

