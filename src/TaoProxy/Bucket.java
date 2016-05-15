package TaoProxy;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @brief Class to represent a bucket
 */
public class Bucket implements Serializable {
    // The blocks in this bucket
    protected Block[] mBlocks;

    // Time stamp for the last time this bucket was updated. Time kept in relation to how many flushes have been done
    private long mUpdateTime;

    // A bitmap to keep track of which slots in this bucket are occupied by a block
    private int mBucketBitmap;

    // Read-write lock for bucket
    private final transient ReentrantReadWriteLock mRWL = new ReentrantReadWriteLock();

    /**
     * @brief Default constructor
     */
    public Bucket() {
        // Initialize blocks to be empty
        mBlocks = new Block[Constants.BUCKET_SIZE];
        for (int i = 0; i < Constants.BUCKET_SIZE; i++) {
            mBlocks[i] = new Block();
        }

        // Set update time
        mUpdateTime = -1;

        // Set bitmap to be 0, indicating that not blocks are currently set for this bucket
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
            // Copy a block from bucket only if bucket has marked that block as occupied in the bitmap
            if (bucket.checkBlockFilled(i) && temp[i] != null) {
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

    /**
     * @brief Method to mark one of the blocks for this bucket as filled in the bitmap
     * @param index
     */
    public void markBlockFilled(int index) {
        int mask = 1 << index;
        mBucketBitmap = mBucketBitmap | mask;
    }

    /**
     * @brief Method to mark one of the blocks for this bucket as unfilled in the bitmap
     * @param index
     */
    public void markBlockUnfilled(int index) {
        int mask = ~(1 << index);
        mBucketBitmap = mBucketBitmap & mask;
    }


    /**
     * @brief Method to check if on of the blocks for this bucket is filled in the bitmap
     * @param index
     * @return whether or not the block at the specified index is filled or not
     */
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
            // Release the write lock
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
        for (int i = 0; i < mBlocks.length; i++) {
            // Before adding the block, make sure that the block is not already occupied
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

    /**
     * @brief Method to return all the blocks that have been properly added, and not just empty blocks
     * @return
     */
    public List<Block> getFilledBlocks() {
        ArrayList<Block> returnList = new ArrayList<>();

        // Get a read lock on the bucket
        mRWL.readLock().lock();

        try {
            for (int i = 0; i < mBlocks.length; i++) {
                // Check to see if the block has been properly added
                if (checkBlockFilled(i)) {
                    // TODO: return copy?
                    returnList.add(mBlocks[i]);
                }
            }
        } finally {
            // Release the read lock
            mRWL.readLock().unlock();
        }

        return returnList;
    }

    /**
     * @brief Method that will attempt to get the data from the block with the specified blockID, if such a block exists
     * within this bucket
     * @param blockID
     * @return the data within the block with block ID == blockID, or null if no such block exists in bucket
     */
    public byte[] getDataFromBlock(long blockID) {
        // Get read lock
        mRWL.readLock().lock();

        try {
            for (int i = 0; i < mBlocks.length; i++) {
                // Check to see if the block is filled, and then check to see if the blockID matches the target blockID
                if (checkBlockFilled(i) && mBlocks[i].getBlockID() == blockID) {
                    return mBlocks[i].getData();
                }
            }
        } finally {
            // Release the read lock
            mRWL.readLock().unlock();
        }

        return null;
    }

    /**
     * @brief Method to clear bucket by clearing the bitmap
     */
    public void clearBucket() {
        // Get read lock
        mRWL.writeLock().lock();

        try {
            for (int i = 0; i < Constants.BUCKET_SIZE; i++) {
                markBlockUnfilled(i);
            }
        } finally {
            // Release the read lock
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
    public boolean modifyBlock(long blockID, byte[] data) {
        boolean writeStatus = false;
        try {
            mRWL.writeLock().lock();
            for (int i = 0; i < Constants.BUCKET_SIZE; i++) {
                if (checkBlockFilled(i) && mBlocks[i].getBlockID() == blockID) {
                    mBlocks[i].setData(data);
                    writeStatus = true;
                }
            }
        } finally {
            mRWL.writeLock().unlock();
        }
        return writeStatus;
    }

    /**
     * @brief Method to statically get the size of all buckets, without the initalization vector or padding needed for
     * encryption
     * @return
     */
    public static int getBucketSize() {
        int updateTimeSize = 8;
        int bitmapSize = 4;
        return updateTimeSize + bitmapSize + Constants.BUCKET_SIZE * (Constants.BLOCK_META_DATA_SIZE + Constants.BLOCK_SIZE);
    }

    /**
     * @brief Method to return this bucket in bytes
     * @return the serialized version of this bucket as a byte array
     */
    public byte[] serialize() {
        //
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
