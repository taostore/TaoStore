package TaoProxy;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @brief Class that represents the subtree component of the proxy
 */
public class TaoSubtree implements Subtree {
    // Map that maps a block ID to the bucket that contains that block
    private Map<Long, Bucket> mBlockMap;

    // Map that maps a path ID to a path
    private SubtreeBucket mRoot;

    /**
     * @brief Default constructor
     */
    public TaoSubtree() {
        mBlockMap = new ConcurrentHashMap<>();
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
            mRoot = new SubtreeBucket(path.getBucket(0));
        }

        // Get the directions for this path
        boolean[] pathDirection = Utility.getPathFromPID(path.getID(), TaoProxy.TREE_HEIGHT);
        System.out.println("we are turning how many times " + pathDirection.length);
        // Keep track of current bucket
        SubtreeBucket currentBucket = mRoot;

        // Keep track of where on the path we are
        int i = 1;
        for (Boolean right : pathDirection) {
            // Determine whether the path is turning left or right from current bucket
            if (right) {
                // Attempt to initialize right bucket
                currentBucket.initializeRight(path.getBucket(i));

                // Move to next bucket
                currentBucket = currentBucket.getRight();
            } else {
                // Attempt to initialize left bucket
                currentBucket.initializeLeft(path.getBucket(i));

                // Move to next bucket
                currentBucket = currentBucket.getLeft();
            }

            // Add blocks in bucket into map
            Block[] blocksToAdd = path.getBucket(i).getBlocks();
            for (Block b : blocksToAdd) {
                mBlockMap.put(b.getBlockID(), currentBucket);
            }
            i++;
        }
    }

    @Override
    public Path getPath(long pathID) {
        // Create path and insert the root of tree
        Path returnPath = new Path(pathID);
        returnPath.addBucket(mRoot);

        // Get the directions for this path
        boolean[] pathDirection = Utility.getPathFromPID(pathID, TaoProxy.TREE_HEIGHT);

        // Keep track of current bucket
        SubtreeBucket currentBucket = mRoot;

        for (Boolean right : pathDirection) {
            currentBucket = right ? currentBucket.getRight() : currentBucket.getLeft();
            returnPath.addBucket(currentBucket);
        }

        return returnPath;
    }

    @Override
    public Path getPathToFlush(long pathID) {
        // Create path and insert the root of tree
        Path returnPath = new Path(pathID);

        for (Block b : mRoot.getBlocks()) {
            mBlockMap.remove(b.getBlockID());
        }

        mRoot.clearBucket();

        returnPath.addBucket(mRoot);

        // Get the directions for this path
        boolean[] pathDirection = Utility.getPathFromPID(pathID, TaoProxy.TREE_HEIGHT);

        // Keep track of current bucket
        SubtreeBucket currentBucket = mRoot;

        for (Boolean right : pathDirection) {
            System.out.println("how many");
            currentBucket = right ? currentBucket.getRight() : currentBucket.getLeft();

            for (Block b : currentBucket.getBlocks()) {
                mBlockMap.remove(b.getBlockID());
            }

            currentBucket.clearBucket();

            returnPath.addBucket(currentBucket);
        }

        return returnPath;
    }

    public void flushPath(long pathID, PriorityQueue<Block> blockHeap) {
        // Create path and insert the root of tree
        Path pathToFlush = getPath(pathID);

        pathToFlush.lockPath();

        for (int i = 0; i < pathToFlush.getPathHeight(); i++) {
            pathToFlush.getBucket(i).clearBucket();
        }

        // TODO: add to path

        pathToFlush.unlockPath();
    }

    public void mapBlockToBucket(long blockID, Bucket bucket) {
        mBlockMap.put(blockID, bucket);
    }
}
