package TaoProxy;

import java.io.Serializable;

/**
 * @brief Class to represent a path
 */
public class Path implements Serializable {
    // The buckets in this path
    private Bucket[] mBuckets;

    // The path ID that this path corresponds to
    private long mID;

    /**
     * @brief Default constructor
     */
    public Path() {
        mID = -1;
        mBuckets = new Bucket[TaoProxy.TREE_HEIGHT];
    }

    /**
     * @brief Constructor that takes in a path ID
     * @param pathID
     */
    public Path(long pathID) {
        mID = pathID;
        mBuckets = new Bucket[TaoProxy.TREE_HEIGHT];
    }

    /**
     * @brief Method to insert a bucket into the path at the specified level
     * @param bucket
     * @param level
     */
    public void insertBucket(Bucket bucket, int level) {
        if (bucket == null) {
            mBuckets[level] = new Bucket();
            return;
        }

        mBuckets[level] = new Bucket(bucket);
    }

    /**
     * @brief Method to add bucket into first empty level on path
     * @param bucket
     * @return
     */
    public boolean addBucket(Bucket bucket) {
        for (int i = 0; i < mBuckets.length; i++) {
            if (mBuckets[i] == null) {
                mBuckets[i] = new Bucket(bucket);
                return true;
            }
        }

        return false;
    }

    /**
     * @brief Accessor method to get all buckets in path
     * @return mBuckets
     */
    public Bucket[] getBuckets() {
        return mBuckets;
    }

    /**
     * @brief Method to get the bucket at a specified level in path
     * @param level
     * @return
     */
    public Bucket getBucket(int level) {
        if (level < mBuckets.length) {
            return mBuckets[level];
        }

        return null;
    }

    /**
     * @brief Accessor method to get the path ID
     * @return mID
     */
    public long getID() {
        return mID;
    }
}
