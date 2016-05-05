package TaoProxy;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @brief Class to represent a bucket
 */
public class Bucket implements Serializable {
    // The blocks in this bucket
    protected Block[] mBlocks;

    // Time stamp for the last time this bucket was updated. Time kept in relation to how many flushes have been done
    private long mUpdateTime;

    private int mBucketBitmap;

    // Read-write lock for bucket
    private final transient ReentrantReadWriteLock mRWL = new ReentrantReadWriteLock();

    /**
     * @brief Default constructor
     */
    public Bucket() {
        mBlocks = new Block[Constants.BUCKET_SIZE];

        for (int i = 0; i < Constants.BUCKET_SIZE; i++) {
            mBlocks[i] = new Block();
        }

        mUpdateTime = -1;
        mBucketBitmap = 0;
    }

    /**
     * @brief Copy constructor
     * @param bucket
     */
    public Bucket(Bucket bucket) {
        mBlocks = new Block[Constants.BUCKET_SIZE];
        Block[] temp = bucket.getBlocks();

        for (int i = 0; i < mBlocks.length; i++) {
            if (temp[i] != null) {
                mBlocks[i] = new Block(temp[i]);
                markBlockFilled(i);
            } else {
                mBlocks[i] = new Block();
            }
        }

        mUpdateTime = bucket.getUpdateTime();
    }

    /**
     * @brief Constructor that takes in an array of bytes to be parsed as a Bucket
     * @param serializedData
     */
    public Bucket(byte[] serializedData) {
        mUpdateTime = Longs.fromByteArray(Arrays.copyOfRange(serializedData, 0, 8));
        mBucketBitmap = Ints.fromByteArray(Arrays.copyOfRange(serializedData, 8, 12));

        mBlocks = new Block[Constants.BUCKET_SIZE];

        int entireBlockSize = Constants.BLOCK_META_DATA_SIZE + Constants.BLOCK_SIZE;
        for (int i = 0; i < mBlocks.length; i++) {
            mBlocks[i] = new Block(Arrays.copyOfRange(serializedData, 12 + entireBlockSize * i, 12 + entireBlockSize + entireBlockSize * i));
        }
    }

    public void markBlockFilled(int index) {
        int mask = 1 << index;
        mBucketBitmap = mBucketBitmap | mask;
    }

    public void markBlockUnfilled(int index) {
        int mask = ~(1 << index);
        mBucketBitmap = mBucketBitmap & mask;
    }

    public boolean checkBlockFilled(int index) {
        int mask = 1 << index;
        return (mBucketBitmap & mask) == mask;
    }

    /**
     * @brief Method to add a block to this bucket as well as update the timestamp
     * @param block
     * @param time
     * @return if block was successfully added or not
     */
    public boolean addBlock(Block block, long time) {
        // Get write lock for this bucket
        mRWL.writeLock().lock();

        // Whether or not the adding is successful
        boolean success = false;

        try {
            // Try to add block
            success = addBlock(block);

            // Update the timestamp of add was successful
            if (success) {
                mUpdateTime = time;
            }
        } finally {
            mRWL.writeLock().unlock();
        }

        return success;
    }

    /**
     * @brief Method that will attempt to add block to bucket
     * @param block
     * @return if block was successfully added or not
     */
    private boolean addBlock(Block block) {
        // Search bucket to see if there is any remaining slots to add block
        for (int i = 0; i < Constants.BUCKET_SIZE; i++) {
            if (!checkBlockFilled(i)) {
                mBlocks[i] = block;
                markBlockFilled(i);
                return true;
            }
        }

        return false;
    }

    /**
     * @brief Accessor method to get all the blocks in this bucket
     * @return mBlocks
     */
    public Block[] getBlocks() {
        // TODO: need lock?
        return mBlocks;
    }

    public byte[] getDataFromBlock(long blockID) {
        // Get read lock
        mRWL.readLock().lock();
        try {

        } finally {
            mRWL.readLock().unlock();
        }

        return null;
    }

    /**
     * @brief Method to clear bucket
     * TODO: still needed?
     */
    public void clearBucket() {
        mRWL.writeLock().lock();
        try {
            for (int i = 0; i < Constants.BUCKET_SIZE; i++) {
                markBlockUnfilled(i);
            }
        } finally {
            mRWL.writeLock().unlock();
        }

    }

    /**
     * @brief Accessor method to get the last time this bucket was updated
     * @return mUpdateTime
     */
    public long getUpdateTime() {
        return mUpdateTime;
    }

    /**
     * @brief Method to get block with given block ID from bucket. Returns null if blockID not found
     * @param blockID
     * @return block with block ID == blockID. null if not found
     */
    public Block getBlock(long blockID) {
        for (int i = 0; i < Constants.BUCKET_SIZE; i++) {
            if (checkBlockFilled(i) && mBlocks[i].getBlockID() == blockID) {
                // TODO: return copy?
                return mBlocks[i];
            }
        }
        return null;
    }

    /**
     * @brief Method to modify the data of a block within this bucket
     * @param blockID
     * @param data
     */
    public void modifyBlock(long blockID, byte[] data) {
        for (int i = 0; i < Constants.BUCKET_SIZE; i++) {
            if (mBlocks[i].getBlockID() == blockID) {
                mBlocks[i].setData(data);
            }
        }
    }

    public static int getBucketSize() {
        return 8 + 4 + Constants.BUCKET_SIZE * (Constants.BLOCK_META_DATA_SIZE + Constants.BLOCK_SIZE);
    }

    public byte[] serialize() {
        byte[] returnData = new byte[Bucket.getBucketSize()];
        byte[] timeBytes = Longs.toByteArray(mUpdateTime);
        System.arraycopy(timeBytes, 0, returnData, 0, timeBytes.length);

        byte[] bitmapBytes = Ints.toByteArray(mBucketBitmap);

        System.arraycopy(bitmapBytes, 0, returnData, timeBytes.length, bitmapBytes.length);

        int metaDataSize = timeBytes.length + bitmapBytes.length;

        int entireBlockSize = Constants.BLOCK_META_DATA_SIZE + Constants.BLOCK_SIZE;
        for(int i = 0; i < Constants.BUCKET_SIZE; i++) {
            System.arraycopy(mBlocks[i].serialize(), 0, returnData, metaDataSize + entireBlockSize * i, entireBlockSize);
        }

        return returnData;
    }

    public void lockBucketWrite() {
        mRWL.writeLock().lock();
    }

    public void unlockBucketWrite() {
        mRWL.writeLock().unlock();
    }
}
