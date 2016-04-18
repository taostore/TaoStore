package TaoProxy;

import java.io.ByteArrayInputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * @brief Class to represent a block
 */
public class Block implements Serializable {
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
     * @brief Constructor that takes in a block ID
     * @param blockID
     */
    public Block(long blockID) {
        mID = blockID;
        mData = new byte[Constants.BLOCK_SIZE];
    }

    /**
     * @brief Copy constructor
     * @param block
     */
    public Block(Block block) {
        mID = block.getBlockID();
        mData = block.getData();
    }

    /**
     * @brief Constructor that takes in an array of bytes to be parsed as a Block
     * @param serializedData
     */
    public Block(byte[] serializedData) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(serializedData);
            ObjectInput in = new ObjectInputStream(bis);
            Block b = (Block) in.readObject();
            mID = b.getBlockID();
            mData = getData();
        } catch (Exception e) {
            mID = -1;
            mData = new byte[Constants.BLOCK_SIZE];
        }
    }

    /**
     * @brief Accessor method to get the data of this block
     * @return copy of mData
     */
    public byte[] getData() {
        byte[] returnData = new byte[Constants.BLOCK_SIZE];
        System.arraycopy(mData, 0, returnData, 0, Constants.BLOCK_SIZE);
        return returnData;
    }

    /**
     * @brief Mutator method to set the data for the block
     * @param data
     */
    public void setData(byte[] data) {
        System.arraycopy(data, 0, mData, 0, Constants.BLOCK_SIZE);
    }

    /**
     * @brief Accessor method to get the ID of this block
     * @return mID
     */
    public long getBlockID() {
        return mID;
    }

    /**
     * @brief Mutator method to set the ID of this block
     * @param blockID
     */
    public void setBlockID(long blockID) {
        mID = blockID;
    }
}
