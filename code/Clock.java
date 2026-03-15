/*
    authors:        Cathal Dwyer, Giuseppe Esposito;
    
    stN:            22391376, 22702205;
    
    date:           15/03/2026;
    
    description:    this is the Clock utility provies static methods:
                        - to get the current tick 
                        - to sleep a thread for N number of ticks;

    approach:       record the start time of the simulation to get the time passed from the start
                    in ms (now - start) and then divide it by the number of millisecond in a tick
*/
public class Clock{
    private static final long START_TIME_MS = System.currentTimeMillis();

    private Clock() {}

    public static long getTick() {
        return (System.currentTimeMillis() - START_TIME_MS) / WarehouseConfig.TICK_MS;
    }

    public static void sleepTicks(long ticks) {
        if (ticks <= 0) return;
        try {
            Thread.sleep(ticks * WarehouseConfig.TICK_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
