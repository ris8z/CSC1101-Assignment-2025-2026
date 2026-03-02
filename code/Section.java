import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Section {

    private final String name;
    private final int capacity;
    private final Queue<String> boxes = new LinkedList<>();

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition stockerDone = lock.newCondition();
    private final Condition stockArrived = lock.newCondition();
    private final Condition spaceFreed = lock.newCondition();

    private boolean stockerActive = false;
    private int pickersWaiting = 0;

    public Section(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
    }

    public void acquireStockerLock() throws InterruptedException {
        lock.lock();
        try {
            while (stockerActive) stockerDone.await();
            stockerActive = true;
        } finally {
            lock.unlock();
        }
    }

    public void releaseStockerLock() {
        lock.lock();
        try {
            stockerActive = false;
            stockerDone.signalAll();
            stockArrived.signalAll();
        } finally {
            lock.unlock();
        }
    }

    // Returns false if section is full (stocker should release lock and wait/move on)
    public boolean addBox(String boxLabel) throws InterruptedException {
        lock.lock();
        try {
            if (boxes.size() >= capacity) {
                return false; // full — caller must release stocker lock
            }
            boxes.add(boxLabel);
            stockArrived.signalAll();
            return true;
        } finally {
            lock.unlock();
        }
    }

    // Stocker calls this after releasing lock to wait for space to open up
    public void waitForSpace() throws InterruptedException {
        lock.lock();
        try {
            while (boxes.size() >= capacity) spaceFreed.await();
        } finally {
            lock.unlock();
        }
    }

    // Waits if stocker is active or section is empty
    public String takeBox() throws InterruptedException {
        lock.lock();
        try {
            pickersWaiting++;
            try {
                while (stockerActive) stockerDone.await();
                while (boxes.isEmpty()) {
                    stockArrived.await();
                    while (stockerActive) stockerDone.await();
                }
                String box = boxes.poll();
                spaceFreed.signalAll(); // notify any stocker waiting for space
                return box;
            } finally {
                pickersWaiting--;
            }
        } finally {
            lock.unlock();
        }
    }

    public String getName()         { return name; }
    public int getCapacity()        { return capacity; }

    public int getBoxCount() {
        lock.lock();
        try { return boxes.size(); }
        finally { lock.unlock(); }
    }

    public boolean isFull() {
        lock.lock();
        try { return boxes.size() >= capacity; }
        finally { lock.unlock(); }
    }

    public int getPickersWaiting() {
        lock.lock();
        try { return pickersWaiting; }
        finally { lock.unlock(); }
    }

    public void preload(int count) {
        int toLoad = Math.min(count, capacity);
        for (int i = 0; i < toLoad; i++) boxes.add(name);
    }
}
