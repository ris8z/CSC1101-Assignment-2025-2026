import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Section {

    private final String name;
    private final Queue<String> boxes = new LinkedList<>();

    // Fair lock to reduce starvation
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition stockerDone = lock.newCondition();
    private final Condition stockArrived = lock.newCondition();

    private boolean stockerActive = false;
    private int pickersWaiting = 0;

    public Section(String name) {
        this.name = name;
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

    public void addBox(String boxLabel) {
        lock.lock();
        try {
            boxes.add(boxLabel);
            stockArrived.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public int availableCapacity() {
        lock.lock();
        try {
            return Integer.MAX_VALUE - boxes.size();
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
                return boxes.poll();
            } finally {
                pickersWaiting--;
            }
        } finally {
            lock.unlock();
        }
    }

    public String getName() { return name; }

    public int getBoxCount() {
        lock.lock();
        try { return boxes.size(); }
        finally { lock.unlock(); }
    }

    public int getPickersWaiting() {
        lock.lock();
        try { return pickersWaiting; }
        finally { lock.unlock(); }
    }

    public void preload(int count) {
        for (int i = 0; i < count; i++) boxes.add(name);
    }
}
