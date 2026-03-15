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

                    the class exposes 2 methods:
                       1. addDelivery  (used by DeliveryThread to add 10 packages to the area)
                       2. takeUpToTen  (used by the StokerThread to try and pick 10 packages from the area)


    approach:       1 Lock to enusre that just 1 stocker is interacting with this area at time,
                    used (ReentrantLock(true)) with the args = true it ensure fairness so no stocker should get in starvation

                    A queue of section names is used as DataStrucure i.e. ["books", "books", "eletronics"].
                    We used LinkedBlockingQueue because it's a thread safe producer-consumer queue, this ensure
                    that the first packege that gets in is the first to go out, but more impotatnly ensure
                    that we can add pakeges and remove it at the same time. (so as assumed stocker + deliver can work together)
*/
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;


public class StagingArea {
    private final LinkedBlockingQueue<String> boxQueue;     // Thread-safe DS so stocker and delivery can work together
    private final ReentrantLock stockerLock;                // lock to ensure just one stocker at time is working here

    public StagingArea(){
        this.boxQueue = new LinkedBlockingQueue<>();
        this.stockerLock = new ReentrantLock(true);
    }

    public void addDelivery(Map<String, Integer> delivery) {
        delivery.forEach((section, count) -> boxQueue.addAll(Collections.nCopies(count, section)));
    }

    public Map<String, Integer> takeUpToTen() throws InterruptedException {
        stockerLock.lockInterruptibly();                    // only 1 stocker allowed
        try {
            Map<String, Integer> load = new HashMap<>();
            
            String firstBox = boxQueue.take();              // wait for at least 1 box to arrive
            load.put(firstBox, 1);
            
            for (int i = 1; i < 10; i++) {                  // try to grab 9 more if they are there
                String box = boxQueue.poll();
                if (box == null)                            // the queue is empty quit
                    break;
                load.merge(box, 1, Integer::sum);
            }

            return load;
        } finally {
            stockerLock.unlock();                           // let the next stocker in 
        }
    }
}

