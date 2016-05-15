package TaoServer;

import TaoProxy.Constants;
import TaoProxy.ServerResponse;

import javax.crypto.SecretKey;

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

    /**
     * @brief Method that will calculate the total storage requirements, in MB, for the ORAM tree based on the specified
     * height of the tree
     * @param treeHeight
     */
    public static long calculateSize(int treeHeight) {
        // Given the height of tree, we now find the amount of buckets we need to make this a full binary tree
        long numBuckets = (long) Math.pow(2, treeHeight + 1) - 1;

        long newBucketSize = ServerConstants.BUCKET_SIZE;

        if ((newBucketSize % Constants.IV_SIZE) != 0) {
            newBucketSize += Constants.IV_SIZE - (newBucketSize % Constants.IV_SIZE);
        }

        ServerConstants.BUCKET_SIZE = newBucketSize;

        // We can now calculate the total size of the system
        return numBuckets * ServerConstants.BUCKET_SIZE;
    }

    /**
     * @brief Method that will calculate the height for the ORAM tree based on storageSize, which is the minimum amount
     * of data, in MB, which most be available for storage
     * @param storageSize
     * @return
     */
    public static int calculateHeight(long storageSize) {
        // Keep track of the storage size
        long totalTreeSize = storageSize;
        long s = totalTreeSize % ServerConstants.TOTAL_BLOCK_SIZE;

        // Pad totalTreeSize so that we have a whole number of blocks
        if ((totalTreeSize % ServerConstants.TOTAL_BLOCK_SIZE) != 0) {
            totalTreeSize += ServerConstants.TOTAL_BLOCK_SIZE - (totalTreeSize % ServerConstants.TOTAL_BLOCK_SIZE);
        }

        // Calculate how many blocks we currently have
        long numBlocks = totalTreeSize / ServerConstants.TOTAL_BLOCK_SIZE;

        // Pad the number of blocks so we have a whole number of buckets
        if ((numBlocks % ServerConstants.NUM_BLOCKS_IN_BUCKET) != 0) {
            numBlocks += ServerConstants.NUM_BLOCKS_IN_BUCKET - (numBlocks % ServerConstants.NUM_BLOCKS_IN_BUCKET);
        }

        // Calculate the number of buckets we currently have
        long numBuckets = numBlocks / ServerConstants.NUM_BLOCKS_IN_BUCKET;

        // Calculate the height of our tree given the number of buckets we have
        return (int) Math.ceil((Math.log(numBuckets + 1) / Math.log(2)) - 1);
    }
}
