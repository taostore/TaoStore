package TaoProxy;

import Configuration.TaoConfigs;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

import java.util.Arrays;

/**
 * @brief Implementation of a path for TaoStore implementing the Path interface
 */
public class TaoPath implements Path {
    // The buckets in this path
    private Bucket[] mBuckets;

    // The path ID that this path corresponds to
    private long mID;

    // Bitmap to hold which buckets in path are free
    private int mPathBitmap;

    /**
     * @brief Default constructor
     */
    public TaoPath() {
        mID = 0;
        mBuckets = new Bucket[TaoConfigs.TREE_HEIGHT + 1];
        for (int i = 0; i < mBuckets.length; i++) {
            mBuckets[i] = new TaoBucket();
        }
        mPathBitmap = 0;
    }

    /**
     * @brief Constructor that takes in a path ID
     * @param pathID
     */
    public TaoPath(long pathID) {
        mID = pathID;
        mBuckets = new Bucket[TaoConfigs.TREE_HEIGHT + 1];

        for (int i = 0; i < mBuckets.length; i++) {
            mBuckets[i] = new TaoBucket();
        }

        mPathBitmap = 0;
    }

    @Override
    public void initFromSerialized(byte[] serialized) {
        mID = Longs.fromByteArray(Arrays.copyOfRange(serialized, 0, 8));

        fillBitmap();

        mBuckets = new Bucket[TaoConfigs.TREE_HEIGHT + 1];

        int entireBucketSize = TaoConfigs.BUCKET_SIZE;

        for (int i = 0; i < mBuckets.length; i++) {
            mBuckets[i] = new TaoBucket();
            mBuckets[i].initFromSerialized(Arrays.copyOfRange(serialized, 8 + entireBucketSize * i, 8 + entireBucketSize + entireBucketSize * i));
        }
    }

    /**
     * @brief Private helper method to fill all the entries in the path bitmap
     */
    private void fillBitmap() {
        for (int i = 0; i < TaoConfigs.TREE_HEIGHT + 1; i++) {
            int mask = 1 << i;
            mPathBitmap = mPathBitmap | mask;
        }
    }

    /**
     * @brief Private helper method to fill the specified entry in the path bitmap
     * @param index
     */
    private void markBucketFilled(int index) {
        int mask = 1 << index;
        mPathBitmap = mPathBitmap | mask;
    }

    /**
     * @brief Private helper method to remove the specified entry in the path bitmap
     * @param index
     */
    private void markBucketUnfilled(int index) {
        int mask = ~(1 << index);
        mPathBitmap = mPathBitmap & mask;
    }

    /**
     * @bief Private helepr method to check if the specified entry in the path bitmap is filled
     * @param index
     * @return
     */
    private boolean checkBucketFilled(int index) {
        int mask = 1 << index;
        return (mPathBitmap & mask) == mask;
    }

    @Override
    public long getPathID() {
        return mID;
    }

    @Override
    public void setPathID(long pathID) {
        mID = pathID;
    }

    @Override
    public void insertBucket(Bucket bucket, int level) {
        if (bucket == null) {
            mBuckets[level] = new TaoBucket();
            return;
        }

        mBuckets[level] = new TaoBucket();
        mBuckets[level].initFromBucket(bucket);

        markBucketFilled(level);
    }

    @Override
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

    @Override
    public Bucket[] getBuckets() {
        return mBuckets;
    }

    @Override
    public Bucket getBucket(int level) {
        if (checkBucketFilled(level)) {
            return mBuckets[level];
        }
        return null;
    }

    @Override
    public byte[] serialize() {
        byte[] idBytes = Longs.toByteArray(mID);

        byte[] serializedBuckets = mBuckets[0].serialize();

        for(int i = 1; i < mBuckets.length; i++) {
            serializedBuckets = Bytes.concat(serializedBuckets, mBuckets[i].serialize());
        }

        return Bytes.concat(idBytes, serializedBuckets);
    }

    @Override
    public byte[] serializeBuckets() {
        byte[] returnData = new byte[TaoConfigs.PATH_SIZE];
        int entireBucketSize = TaoConfigs.BUCKET_SIZE;

        for(int i = 0; i < mBuckets.length; i++) {
            System.arraycopy(mBuckets[i].serialize(), 0, returnData, entireBucketSize * i, entireBucketSize);
        }

        return returnData;
    }

    @Override
    public int getPathHeight() {
        return mBuckets.length - 1;
    }

    @Override
    public void lockPath() {
        for (int i = 0; i < mBuckets.length; i++) {
            mBuckets[i].lockBucket();
        }
    }

    @Override
    public void unlockPath() {
        for (int i = 0; i < mBuckets.length; i++) {
            mBuckets[i].unlockBucket();
        }
    }
}
