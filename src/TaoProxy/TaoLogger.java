package TaoProxy;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * @brief Class used for logging purposes
 */
public class TaoLogger {
    // TODO: Add levels of trace
    // Whether or not logging is enabled
    public static boolean logOn;
    public static int logLevel = 0;

    /**
     * @brief Used to simply log a new line
     */
    public static void log() {
        if (logOn) {
            System.out.println();
        }
    }

    /**
     * @brief Force a message to log regardless of whether logging is enabled or not
     * @param message
     */
    public static void logForce(String message) {
        System.out.println(System.currentTimeMillis() + " :: " + message);
    }

    /**
     * @brief Log a message if logging is on
     * @param message
     */
    public static void log(String message) {
        if (logOn) {
            String timeStamp = new SimpleDateFormat("yyyy/MM/dd_HH:mm:ss").format(Calendar.getInstance().getTime());
            System.out.println(timeStamp + " :: " + message);
        }
    }

    /**
     * @brief Log a message (bytes) if logging is on
     * @param message
     */
    public static void log(byte message) {
        if (logOn) {
            System.out.println(message);
        }
    }

    /**
     * @brief Log a message if the provided level is greater than or equal to the current log level
     * @param message
     * @param level
     */
    public static void log(String message, int level) {
        if (level >= logLevel) {
            System.out.println(message);
        }
    }
}
