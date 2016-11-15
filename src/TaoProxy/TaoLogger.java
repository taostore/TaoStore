package TaoProxy;

/**
 * @brief Class used for logging purposes
 */
public class TaoLogger {
    // Current log level
    public static int logLevel = 0;

    // Constants for log level
    public static int LOG_OFF = 999;
    public static int LOG_DEBUG = 0;
    public static int LOG_INFO = 1;
    public static int LOG_WARNING = 2;
    public static int LOG_ERROR = 3;



    /**
     * @brief Force a message to log regardless of level
     * @param message
     */
    public static void logForce(String message) {
        System.out.println(System.currentTimeMillis() + " :: " + message);
    }


    /**
     * @brief Force a message to log regardless of level
     * @param message
     * @param requestID
     */
    public static void logForceWithReqID(String message, long requestID) {
        System.out.println(System.currentTimeMillis() + " :: reqID #" + requestID + " :: " + message);
    }


    /**
     * @brief Log a debug level message
     * @param message
     */
    public static void logDebug(String message) {
        if (logLevel <= LOG_DEBUG) {
            System.out.println(System.currentTimeMillis() + " :: " + message);
        }
    }

    /**
     * @brief Log an info level message
     * @param message
     */
    public static void logInfo(String message) {
        if (logLevel <= LOG_INFO) {
            System.out.println(System.currentTimeMillis() + " :: " + message);
        }
    }

    /**
     * @brief Log a warning level message
     * @param message
     */
    public static void logWarning(String message) {
        if (logLevel <= LOG_WARNING) {
            System.out.println(System.currentTimeMillis() + " :: " + message);
        }
    }

    /**
     * @brief Log an error level message
     * @param message
     */
    public static void logError(String message) {
        if (logLevel <= LOG_ERROR) {
            System.out.println(System.currentTimeMillis() + " :: " + message);
        }
    }
}
