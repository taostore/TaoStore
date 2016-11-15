package TaoProxy;

import Messages.ServerResponse;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.util.Arrays;

/**
 * @brief Implementation of a class that implements the ServerResponse message type
 * TODO: Pad?
 */
public class TaoServerResponse implements ServerResponse {
    // Data for path that this response corresponds to
    private boolean mWriteStatus;

    // The path ID that was requested for a read
    private long mPathID;

    // The data that was requested on a read
    private byte[] mEncryptedPath;

    /**
     * @brief Default constructor
     */
    public TaoServerResponse() {
        mWriteStatus = false;
        mPathID = -1;
        mEncryptedPath = null;
    }

    @Override
    public void initFromSerialized(byte[] serialized) {
        int type = Ints.fromByteArray(Arrays.copyOfRange(serialized, 0, 4));
        mWriteStatus = type == 1 ? true : false;

        // If the the length of the serialization is greater than 4, this was a read request
        if (serialized.length > 4) {
            mPathID = Longs.fromByteArray(Arrays.copyOfRange(serialized, 4, 12));
            mEncryptedPath = Arrays.copyOfRange(serialized, 12, serialized.length);
        } else {
            mEncryptedPath = null;
        }
    }

    @Override
    public long getPathID() {
        return mPathID;
    }

    @Override
    public void setPathID(long pathID) {
        mPathID = pathID;
    }

    @Override
    public byte[] getPathBytes() {
        return mEncryptedPath;
    }

    @Override
    public void setPathBytes(byte[] pathBytes) {
        mEncryptedPath = pathBytes;
    }

    @Override
    public boolean getWriteStatus() {
        return mWriteStatus;
    }

    @Override
    public void setIsWrite(boolean status) {
        mWriteStatus = status;
    }

    @Override
    public byte[] serialize() {
        int writeInt = mWriteStatus ? 1 : 0;
        byte[] writeBytes = Ints.toByteArray(writeInt);

        if (mEncryptedPath != null) {
            byte[] pathIDBytes = Longs.toByteArray(mPathID);
            return Bytes.concat(writeBytes, pathIDBytes, mEncryptedPath);
        } else {
            return writeBytes;
        }
    }
}
