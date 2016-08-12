package TaoProxy;

/**
 * Created by ajmagat on 7/17/16.
 */
public class TaoLogger {
    public static boolean logOn;

    public static void log() {
        if (logOn) {
            System.out.println();
        }
    }

    public static void logForce(String message) {
        System.out.println(message);
    }

    public static void log(String message) {
        if (logOn) {
            System.out.println(message);
        }
    }

    public static void log(byte message) {
        if (logOn) {
            System.out.println(message);
        }
    }
}
