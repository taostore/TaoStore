package TaoProxy;

import Configuration.TaoConfigs;

/**
 * @brief Utility class with useful static methods
 */
public class Utility {

    /**
     * @brief Return a boolean array representing a path. true at a given spot in the array means that at that level, the
     *        next node on the path will be the right child
     * @param pathID
     * @param height
     * @return boolean array representing a path
     */
    public static boolean[] getPathFromPID(long pathID, int height) {
        boolean[] ret = new boolean[height];

        // The bit of the path ID we are currently checking
        // If 0, then descend left down path, else right
        int direction;

        // The level we are currently checking to see if it is a right or left
        int level = ret.length - 1;

        // Essentially checking the last bit of that pathID, 0 = go left 1 = go right, then moving on the the next bit
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

    /**
     * @brief Get the greatest level of intersection between two paths
     * @param pathOne
     * @param pathTwo
     * @return greatest level of intersection between two paths
     */
    public static long getGreatestCommonLevel(long pathOne, long pathTwo) {
        // TODO: Check size
        long indexBit = 1 << (TaoConfigs.TREE_HEIGHT - 1);
        int greatestLevel = 0;

        // Look for the first point where paths diverge
        while (greatestLevel < TaoConfigs.TREE_HEIGHT) {
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
