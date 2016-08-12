package TaoProxy;

import Configuration.TaoConfigs;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

import java.util.Arrays;
import java.util.Objects;

/**
 * Created by ajmagat on 6/26/16.
 */
public class TaoBlock implements Block {
    // The ID of this block
    private long mID;

    // The data of this block
    private byte[] mData;

    /**
     * @brief Default constructor
     */
    public TaoBlock() {
        mID = -1;

        mData = new byte[TaoConfigs.BLOCK_SIZE];
    }

    /**
     * @brief Constructor that takes in a block ID
     * @param blockID
     */
    public TaoBlock(long blockID) {
        mID = blockID;
        mData = new byte[TaoConfigs.BLOCK_SIZE];
    }

    @Override
    public void initFromBlock(Block b) {
        mID = b.getBlockID();
        mData = b.getData();
    }

    @Override
    public void initFromSerialized(byte[] serialized) {
        try {
            mID = Longs.fromByteArray(Arrays.copyOfRange(serialized, 0, 8));
            mData = new byte[Constants.BLOCK_SIZE];
            System.arraycopy(serialized, Constants.BLOCK_META_DATA_SIZE, mData, 0, Constants.BLOCK_SIZE);
        } catch (Exception e) {
            mID = -1;
            mData = new byte[Constants.BLOCK_SIZE];
        }
    }

    @Override
    public byte[] getData() {
        if (mData != null) {
            byte[] returnData = new byte[Constants.BLOCK_SIZE];
            System.arraycopy(mData, 0, returnData, 0, Constants.BLOCK_SIZE);
            return returnData;
        }

        return null;
    }

    @Override
    public void setData(byte[] data) {
        if (data != null) {
            System.arraycopy(data, 0, mData, 0, Constants.BLOCK_SIZE);
        } else {
            mData = null;
        }
    }

    @Override
    public long getBlockID() {
        return mID;
    }

    @Override
    public void setBlockID(long blockID) {
        mID = blockID;
    }

    @Override
    public Block getCopy() {
        Block b = new TaoBlock();
        b.initFromBlock(this);
        return b;
    }

    @Override
    public byte[] serialize() {
        byte[] idBytes = Longs.toByteArray(mID);
        return Bytes.concat(idBytes, mData);
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
}
