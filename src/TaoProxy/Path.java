package TaoProxy;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.Serializable;
import java.util.Arrays;

/**
 * @brief Class to represent a path
 */
public class Path implements Serializable {
    // The buckets in this path
    private Bucket[] mBuckets;

    // The path ID that this path corresponds to
    private long mID;

    // Bitmap to hold which buckets in path are free
    private int mPathBitmap;

    /**
     * @brief Default constructor
     */
    public Path() {
        mID = -1;
        mBuckets = new Bucket[TaoProxy.TREE_HEIGHT + 1];
        mPathBitmap = 0;
    }

    /**
     * @brief Constructor that takes in a path ID
     * @param pathID
     */
    public Path(long pathID) {
        mID = pathID;
        mBuckets = new Bucket[TaoProxy.TREE_HEIGHT + 1];

        for (int i = 0; i < TaoProxy.TREE_HEIGHT + 1; i++) {
            mBuckets[i] = new Bucket();
        }

        mPathBitmap = 0;
    }

    public Path(long pathID, byte[] serializedData) {
        mID = pathID;
        fillBitmap();

        mBuckets = new Bucket[TaoProxy.TREE_HEIGHT + 1];

        int entireBucketSize = Bucket.getBucketSize();
        for (int i = 0; i < mBuckets.length; i++) {
            mBuckets[i] = new Bucket(Arrays.copyOfRange(serializedData, entireBucketSize * i, entireBucketSize + entireBucketSize * i));
        }
    }

    public void fillBitmap() {
        for (int i = 0; i < TaoProxy.TREE_HEIGHT + 1; i++) {
            int mask = 1 << i;
            mPathBitmap = mPathBitmap | mask;
        }
    }

    public void markBucketFilled(int index) {
        int mask = 1 << index;
        mPathBitmap = mPathBitmap | mask;
    }

    public void markBucketUnfilled(int index) {
        int mask = ~(1 << index);
        mPathBitmap = mPathBitmap & mask;
    }

    public boolean checkBucketFilled(int index) {

        int mask = 1 << index;
        return (mPathBitmap & mask) == mask;
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

        markBucketFilled(level);
    }

    /**
     * @brief Method to add bucket into first empty level on path
     * @param bucket
     * @return
     */
    public boolean addBucket(Bucket bucket) {
        for (int i = 0; i < mBuckets.length; i++) {
            if (!checkBucketFilled(i)) {
                if (bucket != null) {
                    mBuckets[i] = bucket;
                }
                markBucketFilled(i);
                return true;
            }
        }

        return false;
    }

    /**
     * @brief Method to copy the contents of the passed in bucket into a new bucket on the path
     * @param bucket
     * @return
     */
    public boolean copyBucket(Bucket bucket) {
        for (int i = 0; i < mBuckets.length; i++) {
            if (mBuckets[i] == null) {
                if (bucket != null) {
                    mBuckets[i] = new Bucket(bucket);
                }

                markBucketFilled(i);
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
        if (checkBucketFilled(level)) {
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

    public static int getPathSize() {
        return (TaoProxy.TREE_HEIGHT + 1) * Bucket.getBucketSize();
    }

    public byte[] serialize() {
        byte[] returnData = new byte[Path.getPathSize()];

        int entireBucketSize = Bucket.getBucketSize();

        for(int i = 0; i < mBuckets.length; i++) {
            System.arraycopy(mBuckets[i].serialize(), 0, returnData, entireBucketSize * i, entireBucketSize);
        }
        return returnData;
    }
}
