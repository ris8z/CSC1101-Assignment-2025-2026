import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class WarehouseConfig {
    private static final Properties props = new Properties();

    static {
        try (FileInputStream fis = new FileInputStream("warehouse.properties")) {
            props.load(fis);
        } catch (IOException e) {
            System.err.println("Warning: could not load warehouse.properties, using defaults.");
        }
    }

    private static long   getLong(String key, long def)     {return Long.parseLong(props.getProperty(key, String.valueOf(def)));}
    private static int    getInt(String key, int def)       {return Integer.parseInt(props.getProperty(key, String.valueOf(def)));}
    private static double getDouble(String key, double def) {return Double.parseDouble(props.getProperty(key, String.valueOf(def)));}
    private static String getString(String key, String def) {return props.getProperty(key, def);}


    public static final long TICK_MS = getLong("tick_ms", 100);
    public static final long TICKS_PER_DAY = getLong("ticks_per_day", 1000);

    public static final String[] SECTION_NAMES = getString("section_names", "electronics,books,medicines,clothes,tools").split(",");

    public static final int INITIAL_BOXES_PER_SECTION = getInt("initial_boxes_per_section", 5);
    public static final int SECTION_CAPACITY = getInt("section_capacity", 10);

    public static final double DELIVERY_PROBABILITY = getDouble("delivery_probability", 0.01);
    public static final int BOXES_PER_DELIVERY = getInt("boxes_per_delivery", 10);

    public static final int NUM_STOCKERS = getInt("num_stockers", 1);
    public static final int TRAVEL_BASE_TICKS = getInt("travel_base_ticks", 10);
    public static final int TRAVEL_TICKS_PER_BOX = getInt("travel_ticks_per_box", 1);
    public static final int STOCK_TICKS_PER_BOX = getInt("stock_ticks_per_box", 1);
    public static final int STAGING_TAKE_TICKS = getInt("staging_take_ticks", 1);

    public static final int NUM_PICKERS = getInt("num_pickers", 5);
    public static final int TARGET_PICKS_PER_DAY = getInt("target_picks_per_day", 100);
    public static final int PICK_TICKS = getInt("pick_ticks", 1);
    // Set to -1 for a random seed
    public static final long RANDOM_SEED = getLong("random_seed", 42);
}