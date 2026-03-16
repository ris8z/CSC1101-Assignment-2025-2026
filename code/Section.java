/*
    authors:        Cathal Dwyer, Giuseppe Esposito;
    
    stN:            22391376, 22702205;
    
    date:           15/03/2026;
    

    description:    this rappresent the Section Area that is a "resource" used by 2 actors:
                        - stocker 
                        - picker 

                    By requirements:
                        - if a stocker is interacting with the area no other actor is allowed (neither stockers or pickers).
                        - a stocker will wait (FOR SPACE) if the area is full of boxes.
                        - if a picker is interacting other pickers may interact at the same time.
                        - picker will wait for boxes if the area is empty.

                    Requirements does not define if a stocker needs to wait for all picker to finish
                    (we will assume that it does not care at all)

                    the class exposes various methods that:
                        - allow to aquire and relase locks for pickers and stockers,
                        - allow add boxes and remove them from the section.
                        - it enables access to the number of current pickers waiting.
                       

    approach:       The idea is using a lock with 2 wating rooms (conditions)
                        - stockerWaitingRoom
                        - pickerWaitingRoom 
                    and a variable 'stockerActive' to flag if a stocker is working and therefore blocking everyone else.

                    stocker needs to wait only if another stoker is working (i.e. while stockerActive)
                    stocker when relaesing wake up:
                        - a new possible stocker -> stockerWaitingRoom.signal()
                        - all the picker waiting -> pickerWaitingRoom.segnalAll() 
                    (there are also some helper functions such as addBox and waitForSpace used by the StockerThread)

                    pickers needs to wait for avilable boxes and if a stocker is working (i.e. while box == 0 || stockerActive)
                    after they got their ownership of the box the can relase the lock.

                    we could even just use synchronized but to avoid starvation we decided to go with ReentrantLock(true)
                    that accordingly to the java docs wakes up threads in a FIFO manner

*/
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class Section {
    private final String name;
    private final int capacity;

    private int boxes = 0;             
    private int pickersWaiting = 0;   
    private boolean stockerActive = false;

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition stockerWaitingRoom = lock.newCondition();
    private final Condition pickerWaitingRoom = lock.newCondition();

    public Section(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
    }

    // STOCKER LOGIC
    public void acquireStockerLock() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (stockerActive) {
                stockerWaitingRoom.await();
            }
            stockerActive = true;
        } finally {
            lock.unlock();
        }
    }

    public boolean addBox() throws InterruptedException {
        /* Returns false if the section if full */
        lock.lockInterruptibly();
        try {
            if (boxes >= capacity) 
                return false; 
            
            boxes++;
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void releaseStockerLock() {
        lock.lock();
        try {
            stockerActive = false;
            stockerWaitingRoom.signal();                            // wake up the possible next stocker
            pickerWaitingRoom.signalAll();                          // wake up any pickers waiting for boxes
        } finally {
            lock.unlock();
        }
    }

    public void waitForSpace() throws InterruptedException {
        /* DEPRECATED!
         *
         * this mehtod was used by StockerThread in a previous strategy
         * where we were wating for space insted of going to the next section
         * */
        lock.lockInterruptibly();
        try {
            while (boxes >= capacity) 
                stockerWaitingRoom.await();                         // Wait for a picker to take a box
        } finally {
            lock.unlock();
        }
    }

    // PICKER LOGIC 
    public String takeBox() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            pickersWaiting++; 
            try {
                while (boxes == 0 || stockerActive)
                    pickerWaitingRoom.await();
                
                boxes--;
                stockerWaitingRoom.signalAll();                     // tell stocker that there is space here
                return name;
            } finally {
                pickersWaiting--;
            }
        } finally {
            lock.unlock();
        }
    }

    // GETTERS + PRELOAD
    public String getName()  { return name; }
    public int getCapacity() { return capacity; }
    
    public int getBoxCount() {
        lock.lock();
        try { return boxes; }
        finally { lock.unlock(); }
    }

    public boolean isFull() {
        lock.lock();
        try { return boxes >= capacity; }
        finally { lock.unlock(); }
    }

    public int getPickersWaiting() {
        lock.lock();
        try { return pickersWaiting; }
        finally { lock.unlock(); }
    }

    public void preload(int count)  { 
        lock.lock(); 
        try { boxes = Math.min(count, capacity); }
        finally { lock.unlock(); }
    }
}
