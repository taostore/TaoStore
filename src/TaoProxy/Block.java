package TaoProxy;

/**
 * @brief Class to represent a block
 */
public class Block {
    // The ID of this block
    private long mID;

    // The data of this block
    private byte[] mData;

    /**
     * @brief Default constructor
     */
    public Block() {
        mID = -1;
        mData = new byte[Constants.BLOCK_SIZE];
    }

    /**
     * @brief Constructor that takes in an array of bytes to be parsed as a Block
     * @param serializedData
     */
    public Block(byte[] serializedData) {
        // TODO: Parse serializedData as a block
    }

    /**
     * @brief Method to serialize this block
     * @return This block serialized as a byte array
     */
    public byte[] serialize() {
        return null;
    }
}
