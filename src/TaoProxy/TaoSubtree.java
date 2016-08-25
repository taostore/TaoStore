package TaoProxy;

import Configuration.TaoConfigs;
import com.google.common.collect.Multiset;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @brief Class that represents the subtree component of the proxy
 */
public class TaoSubtree implements Subtree {
    // Map that maps a block ID to the bucket that contains that block
    private Map<Long, Bucket> mBlockMap;

    // Map that maps a path ID to a path
    private SubtreeBucket mRoot;

    // The last level that needs to be saved to subtree
    // Every level greater than lastLevelToSave will be deleted when a path is deleted
    private int lastLevelToSave;

    /**
     * @brief Default constructor
     */
    public TaoSubtree() {
        mBlockMap = new ConcurrentHashMap<>();
    }

    @Override
    public void initRoot() {
        int numServers = TaoConfigs.PARTITION_SERVERS.size();

        // Check if we have more than one server, in which case we must initialize the subtree
        if (numServers > 1) {
            if ((numServers & -numServers) != numServers) {
                // TODO: only use a power of two of the servers
            }

            lastLevelToSave = (numServers / 2) - 1;

            // Initialize the needed amount of top nodes
            mRoot = new TaoSubtreeBucket();


            // Keep track of current level and bucket
            int currentLevel = 1;
            SubtreeBucket b = mRoot;

            // If we need to save more than just the root, we do a recursive preorder
            if (currentLevel < lastLevelToSave) {
                recursivePreorderInit(b, currentLevel);
            }
        } else {
            lastLevelToSave = -1;
        }
    }

    /**
     * @brief Private helper method to initialize the top of the tree in the case of storage partitioning
     * @param b
     * @param level
     */
    private void recursivePreorderInit(SubtreeBucket b, int level) {
        b.setRight(null);
        b.setLeft(null);
        level++;

        if (level >= lastLevelToSave) {
            return;
        }

        recursivePreorderInit(b.getLeft(), level);
        recursivePreorderInit(b.getRight(), level);
    }

    @Override
    public Bucket getBucketWithBlock(long blockID) {
        return mBlockMap.getOrDefault(blockID, null);
    }

    @Override
    public void addPath(Path path) {
        // Check if subtree is empty
        if (mRoot == null) {
            // If empty, initialize root with root of given path
            mRoot = new TaoSubtreeBucket(path.getBucket(0));
        }

        // Get the directions for this path
        boolean[] pathDirection = Utility.getPathFromPID(path.getID(), TaoConfigs.TREE_HEIGHT);

        // Keep track of current bucket
        SubtreeBucket currentBucket = mRoot;

        // Keep track of where on the path we are
        int i = 1;
        for (Boolean right : pathDirection) {
            // Determine whether the path is turning left or right from current bucket
            if (right) {
                // Attempt to initialize right bucket
                currentBucket.setRight(path.getBucket(i));

                // Add blocks in bucket into map
                List<Block> blocksToAdd = currentBucket.getFilledBlocks();
                for (Block b : blocksToAdd) {
                    mBlockMap.put(b.getBlockID(), currentBucket);
                }

                // Move to next bucket
                currentBucket = currentBucket.getRight();
            } else {
                // Attempt to initialize left bucket
                currentBucket.setLeft(path.getBucket(i));

                // Add blocks in bucket into map
                List<Block> blocksToAdd = currentBucket.getFilledBlocks();
                for (Block b : blocksToAdd) {
                    mBlockMap.put(b.getBlockID(), currentBucket);
                }

                // Move to next bucket
                currentBucket = currentBucket.getLeft();
            }


            i++;
        }
    }

    @Override
    public Path getPath(long pathID) {
        // Create path and insert the root of tree
        Path returnPath = new TaoPath(pathID);
        returnPath.addBucket(mRoot);

        // Get the directions for this path
        boolean[] pathDirection = Utility.getPathFromPID(pathID, TaoConfigs.TREE_HEIGHT);

        // Keep track of current bucket
        SubtreeBucket currentBucket = mRoot;

        for (Boolean right : pathDirection) {
            // Get either the right or left child depending on the path
            currentBucket = right ? currentBucket.getRight() : currentBucket.getLeft();

            // Add bucket to path
            returnPath.addBucket(currentBucket);
        }

        // Return path
        return returnPath;
    }

    @Override
    public Path getPathToFlush(long pathID) {
        // Create path and insert the root of tree
        Path returnPath = new TaoPath(pathID);

        // Reset root bucket
        resetBucket(mRoot);

        // Add root bucket to the path
        returnPath.addBucket(mRoot);

        // Get the directions for this path
        boolean[] pathDirection = Utility.getPathFromPID(pathID, TaoConfigs.TREE_HEIGHT);

        // Keep track of current bucket
        SubtreeBucket currentBucket = mRoot;

        // Reset each bucket along the path
        for (Boolean right : pathDirection) {
            // Get either the right or left child depending on the path
            currentBucket = right ? currentBucket.getRight() : currentBucket.getLeft();

            // Reset the bucket
            resetBucket(currentBucket);

            // Add bucket to the path
            returnPath.addBucket(currentBucket);
        }

        // Return path
        return returnPath;
    }

    /**
     * @brief Helper method to remove all the blocks in the bucket b from the block map and to clear the bucket
     * @param b
     */
    private void resetBucket(SubtreeBucket b) {
        // Remove mappings for blockIDs in this bucket, as they will be reassigned
        for (Block block : b.getBlocks()) {
            mBlockMap.remove(block.getBlockID());
        }

        // Clear bucket so that new blocks can be inserted
        b.clearBucket();
    }


    /**
     * @brief Recursive helper method to delete buckets if their timestamp is less than minTime and the path is not in
     * the pathReqMultiSet
     * @param bucket
     * @param pathID
     * @param directions
     * @param level
     * @param minTime
     * @param pathReqMultiSet
     * @return
     */
    public long deleteChild(SubtreeBucket bucket, long pathID, boolean[] directions, int level, long minTime, Set<Long> pathReqMultiSet) {
        if (level >= directions.length) {
            return bucket.getUpdateTime();
        }

        // Check if we want to get the right or left child of this bucket
        SubtreeBucket child = directions[level] ? bucket.getRight() : bucket.getLeft();

        // If this is a leaf node, return
        if (child == null) {
            return bucket.getUpdateTime();
        }

        // Save current level
        int currentLevel = level;

        // Increment level
        level++;

        // Delete descendants and get the timestamp of child
        long timestamp = deleteChild(child, pathID, directions, level, minTime, pathReqMultiSet);

        // Check if we should delete the child
        if (timestamp <= minTime && ! isBucketInSet(pathID, currentLevel, pathReqMultiSet) && level > lastLevelToSave) {
            TaoLogger.log("Deleting because " + timestamp + " < " + minTime);
            // We should delete child, check if it was the right or left child
            if (directions[currentLevel]) {
                TaoLogger.log("Going to delete the right child for path " + pathID + " at level " + currentLevel);
                bucket.setRight(null);
            } else {
                TaoLogger.log("Going to delete the left child for path " + pathID + " at level " + currentLevel);
                bucket.setLeft(null);
            }
        }

        // Return timestamp of this bucket
        return bucket.getUpdateTime();
    }

    /**
     * @brief
     * @param pathID
     * @param level
     * @param pathReqMultiSet
     * @return
     */
    private boolean isBucketInSet(long pathID, int level, Set<Long> pathReqMultiSet) {
        for (Long checkPathID : pathReqMultiSet) {
            if (Utility.getGreatestCommonLevel(pathID, checkPathID) <= level) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void deleteNodes(long pathID, long minTime, Set<Long> pathReqMultiSet) {
        // TODO: need locks?
        // Check if subtree is empty
        if (mRoot == null) {
            // If mRoot is null, the tree will be reinitialized on the next write
            return;
        }

        // Get the directions for this path
        boolean[] pathDirection = Utility.getPathFromPID(pathID, TaoConfigs.TREE_HEIGHT);

        // Try to delete all descendents
        deleteChild(mRoot, pathID, pathDirection, 0, minTime, pathReqMultiSet);

        // Check if we can delete root
        // NOTE: If root has a timestamp less than minTime, the the entire subtree should be able to be deleted, and
        // thus it should be okay to set mRoot to null
        if (mRoot.getUpdateTime() <= minTime && ! pathReqMultiSet.contains(pathID) && 0 > lastLevelToSave) {
            TaoLogger.log("** Deleting the root node too");
            mRoot = null;
        } else {
            TaoLogger.log("** Not deleting root node because either " + mRoot.getUpdateTime() + " > " + minTime + " or pathReqMultiSet.contains(" + pathID + ") is " + pathReqMultiSet.contains(pathID));
        }
    }

    public void mapBlockToBucket(long blockID, Bucket bucket) {
        mBlockMap.put(blockID, bucket);
    }
}
