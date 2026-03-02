public class WarehouseConfig {

    public static final long TICK_MS = 100;
    public static final long TICKS_PER_DAY = 1000;

    public static final String[] SECTION_NAMES = {
        "electronics", "books", "medicines", "clothes", "tools"
    };

    public static final int INITIAL_BOXES_PER_SECTION = 5;

    public static final double DELIVERY_PROBABILITY = 0.01;
    public static final int BOXES_PER_DELIVERY = 10;

    public static final int NUM_STOCKERS = 1;
    public static final int TRAVEL_BASE_TICKS = 10;
    public static final int TRAVEL_TICKS_PER_BOX = 1;
    public static final int STOCK_TICKS_PER_BOX = 1;
    public static final int STAGING_TAKE_TICKS = 1;

    public static final int NUM_PICKERS = 5;
    public static final int TARGET_PICKS_PER_DAY = 100;
    public static final int PICK_TICKS = 1;

    // Set to -1 for a random seed
    public static final long RANDOM_SEED = 42;
}