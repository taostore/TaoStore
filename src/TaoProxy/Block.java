package TaoProxy;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

import java.io.*;
import java.util.Arrays;
import java.util.Objects;

/**
 * @brief Class to represent a block
 */
public class Block implements Serializable {
    // The ID of this block
    private transient long mID;

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
            mID = Longs.fromByteArray(Arrays.copyOfRange(serializedData, 0, 8));
            mData = new byte[Constants.BLOCK_SIZE];
            System.arraycopy(serializedData, Constants.BLOCK_META_DATA_SIZE, mData, 0, Constants.BLOCK_SIZE);
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
        if (mData != null) {
            byte[] returnData = new byte[Constants.BLOCK_SIZE];
            System.arraycopy(mData, 0, returnData, 0, Constants.BLOCK_SIZE);
            return returnData;
        }

        return null;
    }

    /**
     * @brief Mutator method to set the data for the block
     * @param data
     */
    public void setData(byte[] data) {
        if (data != null) {
            System.arraycopy(data, 0, mData, 0, Constants.BLOCK_SIZE);
        } else {
            mData = null;
        }
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

    @Override
    public boolean equals(Object obj) {
        if ( ! (obj instanceof Block) ) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        // Two requests are equal if they have the same request ID
        Block rhs = (Block) obj;

        if (mID != rhs.getBlockID()) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        // TODO: add data?
        return Objects.hash(mID);
    }

    /**
     * @brief Method to return this block in bytes
     * @return serialized version of this block
     */
    public byte[] serialize() {
        byte[] idBytes = Longs.toByteArray(mID);
        return Bytes.concat(idBytes, mData);
    }
}
