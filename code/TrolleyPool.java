/*
    authors:        Cathal Dwyer, Giuseppe Esposito;
    
    stN:            22391376, 22702205;
    
    date:           15/03/2026;
    

    description:    This class represents the Trolley Pool. Actors (Stockers and Pickers) can either:
                        - acquire a trolley
                        - release a trolley
                    Trolleys are a shared resource. (By requirments they are megic and teleport to the actor)

                    Deadlock Prevention:
                    if Picker consume all available trolleys while wating in emtpy sections, stockers
                    will never be able to deliver new boxes -> deadklock state (Circular Wait). 
                    To prevent this the resource allocation is unfair and advatages the stockers.


    approach:       The pool uses a queue of integer IDs protected by a ReentrantLock with two waiting roomes:
                        1. stockerWaitingRoom 
                        2. pickerWaitingRoom
                    
                    - The number of trolley K must be greater than 1 in the system that we designed, othewise
                        deadlock will happen

                    - Unfair queue: we always reserve 1 trolley for stockers:
                        if aviable.size <= 1 -> pickers already needs to wait, while stocker wait only if available.isEmpty()

                    - Priority wake-up: when a trolley is realeased, we first wake up the stocker wating room and then the
                        pickers one
*/
import java.util.Queue;
import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TrolleyPool {
    private final Queue<Integer> available;
    private final ReentrantLock lock;
    private final Condition stockerWaitingRoom;
    private final Condition pickerWaitingRoom;

    public TrolleyPool(int k) {
        if(k <= 1)                                                  // this is because with 1 trolley we can't find a way to avoid deadlock
            k = 2;                                                  // and not violiate the requirements at the same time

        this.available = new LinkedList<>();

        for (int i = 1; i <= k; i++) 
            this.available.add(i);

        this.lock = new ReentrantLock(true);
        this.stockerWaitingRoom = lock.newCondition();
        this.pickerWaitingRoom = lock.newCondition();
    }

    public int acquire(boolean isStocker) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            if (isStocker) {
                /* if is a stocker thread:
                 * put it in the waiting room only if there are 0
                 * trolleys */
                while (available.isEmpty())         
                    stockerWaitingRoom.await();
            } 
            else {
                /* if is a picker thread:
                 * put it in the waiting room even when there still is 
                 * a trolley (bc 1 is always reserved to stockers) 
                 * */
                while (available.size() <= 1 )       
                    pickerWaitingRoom.await();
            }

            return available.poll();
        } finally {
            lock.unlock();
        }
    }

    public void release(int trolleyId) {
        lock.lock();
        try {
            available.add(trolleyId);
            stockerWaitingRoom.signal();                            // precedence to the stockers
            pickerWaitingRoom.signal();
        } finally { lock.unlock();
        }
    }

    public static int defaultK() {
        return (WarehouseConfig.NUM_STOCKERS + WarehouseConfig.NUM_PICKERS) / 2;
    }
}
