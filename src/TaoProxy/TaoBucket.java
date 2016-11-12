package TaoProxy;

import Configuration.TaoConfigs;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @brief Implementation of a bucket for TaoStore implementing the Bucket interface
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
     */
    public TaoBucket() {
        // Initialize blocks to be empty
        mBlocks = new Block[TaoConfigs.BLOCKS_IN_BUCKET];
        for (int i = 0; i < TaoConfigs.BLOCKS_IN_BUCKET; i++) {
            mBlocks[i] = new TaoBlock();
        }

        // Set update time
        mUpdateTime = -1;

        // Set bitmap to be 0, indicating that not blocks are currently set for this bucket
        mBucketBitmap = 0;
    }

    @Override
    public void initFromBucket(Bucket bucket) {
        // Initialize the list of blocks
        mBlocks = new Block[TaoConfigs.BLOCKS_IN_BUCKET];

        // Get the blocks from the bucket to be copied
        Block[] temp = bucket.getBlocks();

        // Add the copied blocks to this bucket
        for (int i = 0; i < mBlocks.length; i++) {
            // Copy a block from bucket only if bucket has marked that block as occupied in the bitmap
            if (bucket.checkBlockFilled(i) && temp[i] != null) {
                mBlocks[i] = temp[i].getCopy();
                markBlockFilled(i);
            } else {
                // If the block is not filled in the bucket to be copied, add an empty block to the bucket
                mBlocks[i] = new TaoBlock();
            }
        }

        // Copy the update time of the copied bucket
        mUpdateTime = bucket.getUpdateTime();
    }

    @Override
    public void initFromSerialized(byte[] serialized) {
        // Get the last time this bucket was updated
        mUpdateTime = Longs.fromByteArray(Arrays.copyOfRange(serialized, 0, 8));

        // Get the bitmap for this bucket
        mBucketBitmap = Ints.fromByteArray(Arrays.copyOfRange(serialized, 8, 12));

        // Initialize the list of blocks
        mBlocks = new Block[TaoConfigs.BLOCKS_IN_BUCKET];

        // Get the actual blocks
        int entireBlockSize = TaoConfigs.TOTAL_BLOCK_SIZE;
        for (int i = 0; i < mBlocks.length; i++) {
            mBlocks[i] = new TaoBlock();
            mBlocks[i].initFromSerialized(Arrays.copyOfRange(serialized, 12 + entireBlockSize * i, 12 + entireBlockSize + entireBlockSize * i));
        }
    }

    /**
     * @brief Private helper method to mark one of the blocks for this bucket as filled in the bitmap
     * @param index
     */
    private void markBlockFilled(int index) {
        int mask = 1 << index;
        mBucketBitmap = mBucketBitmap | mask;
    }

    /**
     * @brief Private helper method to mark one of the blocks for this bucket as unfilled in the bitmap
     * @param index
     */
    private void markBlockUnfilled(int index) {
        int mask = ~(1 << index);
        mBucketBitmap = mBucketBitmap & mask;
    }

    @Override
    public boolean checkBlockFilled(int index) {
        int mask = 1 << index;
        return (mBucketBitmap & mask) == mask;
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
     * @brief Privte helper method that will attempt to add block to bucket
     * @param block
     * @return if block was successfully added or not
     */
    private boolean addBlock(Block block) {
        // Search bucket to see if there is any remaining slots to add block
        for (int i = 0; i < mBlocks.length; i++) {
            // Before adding the block, make sure that the block is not already occupied
            if (!checkBlockFilled(i)) {
                // If we find an empty spot, we add the block and mark the bucket filled
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
        // TODO: Replace this and make it getFilledBlocks?
        return mBlocks;
    }

    @Override
    public List<Block> getFilledBlocks() {
        // Make an arraylist to hold the blocks
        ArrayList<Block> returnList = new ArrayList<>();

        // Get a read lock on the bucket
        mRWL.readLock().lock();

        try {
            for (int i = 0; i < mBlocks.length; i++) {
                // Check to see if the block has been properly added
                if (checkBlockFilled(i)) {
                    // TODO: Add a copy? currently a reference
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
            // Mark all blocks unfilled
            for (int i = 0; i < TaoConfigs.BLOCKS_IN_BUCKET; i++) {
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

    @Override
    public void setUpdateTime(long timestamp) {
        mUpdateTime = timestamp;
    }

    @Override
    public boolean modifyBlock(long blockID, byte[] data) {
        // Whether or not the modifying succeeded
        boolean writeStatus = false;
        try {
            // Get write lock
            mRWL.writeLock().lock();

            // Search for the correct block
            for (int i = 0; i < TaoConfigs.BLOCKS_IN_BUCKET; i++) {
                if (checkBlockFilled(i) && mBlocks[i].getBlockID() == blockID) {
                    // Modify data for the block
                    mBlocks[i].setData(data);
                    writeStatus = true;
                }
            }
        } finally {
            // Release the write lock
            mRWL.writeLock().unlock();
        }

        // Return modify status
        return writeStatus;
    }

    @Override
    public byte[] serialize() {
        // The data to be returned
        byte[] returnData = new byte[TaoConfigs.BUCKET_SIZE];

        // Get the bytes for the update time
        byte[] timeBytes = Longs.toByteArray(mUpdateTime);
        System.arraycopy(timeBytes, 0, returnData, 0, timeBytes.length);

        // Get the bytes for the bitmap
        byte[] bitmapBytes = Ints.toByteArray(mBucketBitmap);
        System.arraycopy(bitmapBytes, 0, returnData, timeBytes.length, bitmapBytes.length);

        // Keep track of the size of the update time and bitmap
        int metaDataSize = timeBytes.length + bitmapBytes.length;

        // Add all the blocks to the return data
        int entireBlockSize = TaoConfigs.TOTAL_BLOCK_SIZE;
        for(int i = 0; i < TaoConfigs.BLOCKS_IN_BUCKET; i++) {
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
