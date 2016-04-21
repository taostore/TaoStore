package TaoServer;

/**
 * Created by ajmagat on 4/20/16.
 */
public class ServerUtility {
    public static boolean[] getPathFromPID(long pathID, int height) {
        boolean[] ret = new boolean[height];

        // The bit of the path ID we are currently checking
        // If 0, then descend left down path, else right
        int direction;

        // The level we are currently checking to see if it is a right or left
        int level = ret.length - 1;

        while (pathID > 0) {
            direction = (int) pathID % 2;

            if (direction == 0) {
                ret[level] = false;
            } else {
                ret[level] = true;
            }
            pathID = pathID >> 1;
            level--;
        }

        return ret;
    }
}
