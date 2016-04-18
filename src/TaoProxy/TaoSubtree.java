package TaoProxy;

import java.util.List;
import java.util.Map;

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
    }

    @Override
    public Bucket getBucketWithBlock(long blockID) {
        return null;
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

        // Keep track of current bucket
        SubtreeBucket currentBucket = mRoot;

        // Keep track of where on the path we are
        int i = 1;
        for (Boolean left : pathDirection) {
            // Determine whether the path is turning left or right from current bucket
            if (left) {
                // Attempt to initialize left bucket
                currentBucket.initializeLeft(path.getBucket(i));

                // Move to next bucket
                currentBucket = currentBucket.getLeft();
            } else {
                // Attempt to initialize right bucket
                currentBucket.initializeRight(path.getBucket(i));

                // Move to next bucket
                currentBucket = currentBucket.getRight();
            }

            i++;
        }
    }

    @Override
    public Path getPath(long pathID) {
        // Get the directions for this path
        boolean[] pathDirection = Utility.getPathFromPID(pathID, TaoProxy.TREE_HEIGHT);

        return null;
    }
}
