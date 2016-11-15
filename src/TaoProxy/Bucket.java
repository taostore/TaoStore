package TaoProxy;

import java.util.List;

/**
 * @brief Class to represent a bucket
 */
public interface Bucket {
    /**
     * @brief Method to add a block to this bucket as well as update the timestamp
     * @param block
     * @param time
     * @return if block was successfully added or not
     */
    boolean addBlock(Block block, long time);

    /**
     * @brief Method to modify the data of a block within this bucket
     * @param blockID
     * @param data
     */
    boolean modifyBlock(long blockID, byte[] data);

    /**
     * @brief Method that will attempt to get the data from the block with the specified blockID, if such a block exists
     * within this bucket
     * @param blockID
     * @return the data within the block with block ID == blockID, or null if no such block exists in bucket
     */
    byte[] getDataFromBlock(long blockID);

    /**
     * @brief Accessor method to get all the blocks in this bucket
     * @return a copy of the blocks
     * TODO: A copy of the blocks?
     */
    Block[] getBlocks();

    /**
     * @brief Method to clear bucket of all blocks
     */
    void clearBucket();

    /**
     * @brief Accessor method to get the last time this bucket was updated
     * @return mUpdateTime
     */
    long getUpdateTime();

    /**
     * @brief Mutator method to set the last time this bucket was updated
     * @param timestamp
     */
    void setUpdateTime(long timestamp);

    /**
     * @brief Method to check if the block at given index for this bucket is filled with valid data
     * @param index
     * @return whether or not the block at the specified index is filled or not
     */
    boolean checkBlockFilled(int index);

    /**
     * @brief Method to return all the blocks that have been properly added, and not just empty blocks
     * @return
     */
    List<Block> getFilledBlocks();

    /**
     * @brief Method to lock bucket for writing
     */
    void lockBucket();

    /**
     * @brief Method to unlock bucket
     */
    void unlockBucket();

    /**
     * @brief Method to return this bucket in bytes
     * @return the serialized version of this bucket as a byte array
     */
    byte[] serialize();

    /**
     * @brief Initialize a bucket with the same data from a given bucket
     * @param bucket
     */
    void initFromBucket(Bucket bucket);

    /**
     * @brief Method to initialize a bucket given the serialization of a bucket (of the same class)
     * @param serialized
     */
    void initFromSerialized(byte[] serialized);
}
