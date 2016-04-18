package TaoProxy;

/**
 * @brief
 */
public class Utility {
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

    public static long getGreatestCommonLevel(long pathOne, long pathTwo) {
        // TODO: Check size
        long indexBit = 1 << (TaoProxy.TREE_HEIGHT - 1);
        int greatestLevel = 0;

        while (greatestLevel < TaoProxy.TREE_HEIGHT) {
            if ( (pathOne & indexBit) == (pathTwo & indexBit) ) {
                indexBit = indexBit >> 1;
                greatestLevel++;
            } else {
                break;
            }
        }

        return greatestLevel;
    }
}
