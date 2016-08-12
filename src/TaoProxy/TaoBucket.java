package TaoProxy;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @brief
 * TODO: Make this implementation agnostic, currently not due to serialization
 */
public class TaoBucket implements Bucket {
    // The blocks in this bucket
    protected Block[] mBlocks;

    // Time stamp for the last time this bucket was updated. Time kept in relation to how many flushes have been done
    private long mUpdateTime;

    // A bitmap to keep track of which slots in this bucket are occupied by a block
    private int mBucketBitmap;

    // Read-write lock for bucket
    private final ReentrantReadWriteLock mRWL = new ReentrantReadWriteLock();

    /**
     * @brief Default constructor
     * TODO: Should be agnostic to Block type
     */
    public TaoBucket() {
        // Initialize blocks to be empty
        mBlocks = new Block[Constants.BUCKET_SIZE];

        // TODO: Should be implementation agnostic
        for (int i = 0; i < Constants.BUCKET_SIZE; i++) {
            mBlocks[i] = new TaoBlock();
        }

        // Set update time
        mUpdateTime = -1;

        // Set bitmap to be 0, indicating that not blocks are currently set for this bucket
        mBucketBitmap = 0;
    }

    @Override
    public void initFromBucket(Bucket bucket) {
        mBlocks = new Block[Constants.BUCKET_SIZE];
        Block[] temp = bucket.getBlocks();

        for (int i = 0; i < mBlocks.length; i++) {
            // Copy a block from bucket only if bucket has marked that block as occupied in the bitmap
            if (bucket.checkBlockFilled(i) && temp[i] != null) {
                mBlocks[i] = temp[i].getCopy();
                markBlockFilled(i);
            } else {
                // TODO: Should be implementation agnostic
                mBlocks[i] = new TaoBlock();
            }
        }

        mUpdateTime = bucket.getUpdateTime();
    }

    @Override
    public void initFromSerialized(byte[] serialized) {
        mUpdateTime = Longs.fromByteArray(Arrays.copyOfRange(serialized, 0, 8));
        mBucketBitmap = Ints.fromByteArray(Arrays.copyOfRange(serialized, 8, 12));

        mBlocks = new Block[Constants.BUCKET_SIZE];

        int entireBlockSize = Constants.BLOCK_META_DATA_SIZE + Constants.BLOCK_SIZE;
        for (int i = 0; i < mBlocks.length; i++) {
            mBlocks[i] = new TaoBlock();
            mBlocks[i].initFromSerialized(Arrays.copyOfRange(serialized, 12 + entireBlockSize * i, 12 + entireBlockSize + entireBlockSize * i));
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

    @Override
    public boolean checkBlockFilled(int index) {
        int mask = 1 << index;
        return (mBucketBitmap & mask) == mask;
    }

    // TODO: do this
    @Override
    public boolean removeBlock(long blockID) {
        return false;
    }

    @Override
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

    @Override
    public Block[] getBlocks() {
        // TODO: need lock?
        // TODO: Copy of blocks?
        return mBlocks;
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
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
     * @brief Method to statically get the size of all buckets, without the initialization vector or padding needed for
     * encryption
     * @return
     */
    // TODO: get this out of here
    public static int getBucketSize() {
        int updateTimeSize = 8;
        int bitmapSize = 4;
        return updateTimeSize + bitmapSize + Constants.BUCKET_SIZE * (Constants.TOTAL_BLOCK_SIZE);
    }

    @Override
    public byte[] serialize() {
        // The data to be returned
        byte[] returnData = new byte[TaoBucket.getBucketSize()];

        // Get the bytes for the update time
        byte[] timeBytes = Longs.toByteArray(mUpdateTime);
        System.arraycopy(timeBytes, 0, returnData, 0, timeBytes.length);

        // Get the bytes for the bitmap
        byte[] bitmapBytes = Ints.toByteArray(mBucketBitmap);
        System.arraycopy(bitmapBytes, 0, returnData, timeBytes.length, bitmapBytes.length);

        // Keep track of the size of the update time and bitmap
        int metaDataSize = timeBytes.length + bitmapBytes.length;

        // Add all the blocks to the return data
        int entireBlockSize = Constants.BLOCK_META_DATA_SIZE + Constants.BLOCK_SIZE;
        for(int i = 0; i < Constants.BUCKET_SIZE; i++) {
            System.arraycopy(mBlocks[i].serialize(), 0, returnData, metaDataSize + entireBlockSize * i, entireBlockSize);
        }

        return returnData;
    }

    @Override
    public void lockBucket() {
        mRWL.writeLock().lock();
    }

    @Override
    public void unlockBucket() {
        mRWL.writeLock().unlock();
    }
}
