package TaoProxy;

/**
 * @brief Interface to represent a block
 */
public interface Block {
    /**
     * @brief
     * @param b
     */
    void initFromBlock(Block b);

    /**
     * @brief
     * @param serialized
     */
    void initFromSerialized(byte[] serialized);

    /**
     * @brief Accessor method to get the data of this block
     * @return copy of mData
     */
    byte[] getData();

    /**
     * @brief Mutator method to set the data for the block
     * @param data
     */
    void setData(byte[] data);

    /**
     * @brief Accessor method to get the ID of this block
     * @return mID
     */
    long getBlockID();

    /**
     * @brief Mutator method to set the ID of this block
     * @param blockID
     */
    void setBlockID(long blockID);

    /**
     * @brief Get a copy of the block
     * @return
     */
    Block getCopy();

    /**
     * @brief Method to return this block in bytes
     * @return serialized version of this block
     */
    byte[] serialize();
}
