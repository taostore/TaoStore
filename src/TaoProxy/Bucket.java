package TaoProxy;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * @brief Class to represent a bucket
 */
public class Bucket implements Serializable {
    // The blocks in this bucket
    protected Block[] mBlocks;

    // Time stamp for the last time this bucket was updated. Time kept in relation to how many flushes have been done
    private long mUpdateTime;

    /**
     * @brief Default constructor
     */
    public Bucket() {
        mBlocks = new Block[Constants.BUCKET_SIZE];
        mUpdateTime = -1;
    }


    /**
     * @brief Copy constructor
     * @param bucket
     */
    public Bucket(Bucket bucket) {
        mBlocks = new Block[Constants.BUCKET_SIZE];
        Block[] temp = bucket.getBlocks();

        for (int i = 0; i < mBlocks.length; i++) {
            mBlocks[i] = new Block(temp[i]);
        }

        mUpdateTime = bucket.getUpdateTime();
    }

    /**
     * @brief Constructor to initialize bucket given the data of all the blocks
     * @param data
     */
    public Bucket(byte[] data) {
        mBlocks = new Block[Constants.BUCKET_SIZE];

        // TODO: Finish, determine if needed
    }

    /**
     * @brief Method to add a block to this bucket as well as update the timestamp
     * @param block
     * @param time
     * @return if block was successfully added or not
     */
    public boolean addBlock(Block block, long time) {
        // Try to add block
        boolean success = addBlock(block);

        // Update the timestamp of add was successful
        if (success) {
            mUpdateTime = time;
        }

        return success;
    }

    /**
     * @brief Method that will attempt to add block to bucket
     * @param block
     * @return if block was successfully added or not
     */
    public boolean addBlock(Block block) {
        // Search bucket to see if there is any remaining slots to add block
        for (int i = 0; i < Constants.BUCKET_SIZE; i++) {
            if (mBlocks[i] == null) {
                mBlocks[i] = new Block(block);
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
        // TODO: Return copy?
        return mBlocks;
    }

    /**
     * @brief Method to clear bucket
     * TODO: still needed?
     */
    public void clearBucket() {
        mBlocks = new Block[Constants.BUCKET_SIZE];
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
            if (mBlocks[i] != null && mBlocks[i].getBlockID() == blockID) {
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
            if (mBlocks[i] != null && mBlocks[i].getBlockID() == blockID) {
                mBlocks[i].setData(data);
            }
        }
    }
}
