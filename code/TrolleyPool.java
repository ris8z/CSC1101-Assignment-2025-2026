/*
    authors:        Cathal Dwyer, Giuseppe Esposito;
    
    stN:            22391376, 22702205;
    
    date:           15/03/2026;
    

    description:    this Rappresent the trolley pool, an actor can either
                        - aquire a trolley
                        - release a trolley
                    these trolley are magic and in fact they get teletransported the the actor that asked for them.

                    These trolley are a shared resource between 2 actors:
                        - stokers
                        - pickers

                    In this case we need to pay attaion a really bad case, if we have a lot of pickers a few stokers
                    and few trolley there is a chache that all our trolleys get owned by pickers
                    ergo -> blocking the state of the simulitaion

                    We don't want that because it's against requirements, so we will create an unfair queue,
                    that always advatages stokers (to ensure that packages are shipped to section and the simulation goes on)


    approach:       each trolley is a node of a linked list (Queue) with it's ID as value, the access to this queue
                    is guarded by a lock with to condition (waiting room) one for stockers thread and the other one
                    for pickers thread, we favour stockers by putting pickers inside the waiting room even if there is
                    a trolley in the queue, in these we there always will be a trolley for the stockers, and the games go on.
                    Also when a trolley comes back we first singal to the stockers waiting room and then to the pickers one.
                    
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
        available = new LinkedList<>();

        for (int i = 1; i <= k; i++) 
            available.add(i);

        lock = new ReentrantLock(true);
        stockerWaitingRoom = lock.newCondition();
        pickerWaitingRoom = lock.newCondition();
    }

    public int acquire(boolean isStocker) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            if (isStocker) {
                /* is a stocker thread 
                 * put it in the waiting room only if there are 0
                 * trolleys */
                while (available.isEmpty())         
                    stockerWaitingRoom.await();
            } 
            else {
                /* is a picker thread 
                 * put it in the waiting room even when there still is 
                 * a trolley (bc 1 is always reserved to stockers) */
                while (available.size() <= 1)       
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
            stockerWaitingRoom.signal();  // precedence to the stockers
            pickerWaitingRoom.signal();
        } finally {
            lock.unlock();
        }
    }

    public static int defaultK() {
        return (WarehouseConfig.NUM_STOCKERS + WarehouseConfig.NUM_PICKERS) / 2;
    }
}
