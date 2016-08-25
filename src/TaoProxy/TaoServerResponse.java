package TaoProxy;

import Messages.ServerResponse;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.util.Arrays;

/**
 * Created by ajmagat on 6/3/16.
 */
public class TaoServerResponse implements ServerResponse {
    // Data for path that this response corresponds to
    private boolean mWriteStatus;

    //
    private long mPathID;
    private byte[] mEncryptedPath;

    /**
     * @brief Default constructor
     */
    public TaoServerResponse() {
        mWriteStatus = false;
        mPathID = -1;
        mEncryptedPath = null;
    }

    /**
     * @brief Constructor that creates a response for the given request
     * @param pathID
     * @param encryptedData
     */
    public TaoServerResponse(long pathID, byte[] encryptedData) {
        mWriteStatus = false;
        mPathID = pathID;
        mEncryptedPath = encryptedData;
    }

    /**
     * @brief
     * @param serializedData
     */
    public TaoServerResponse(byte[] serializedData) {
        int type = Ints.fromByteArray(Arrays.copyOfRange(serializedData, 0, 4));
        mWriteStatus = type == 1 ? true : false;
        if (serializedData.length > 4) {
            mPathID = Longs.fromByteArray(Arrays.copyOfRange(serializedData, 4, 12));
            mEncryptedPath = Arrays.copyOfRange(serializedData, 12, serializedData.length);
        } else {
            mEncryptedPath = null;
        }
    }

    public void initFromSerialized(byte[] serialized) {
        int type = Ints.fromByteArray(Arrays.copyOfRange(serialized, 0, 4));
        mWriteStatus = type == 1 ? true : false;
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

    public void setIsWrite(boolean status) {
        mWriteStatus = status;
    }

    /**
     *
     * @return
     */
    public static int getServerResponseSize() {
        return TaoPath.getPathSize() + 4;
    }

    public static int getServerResponseSizeWrite() {
        return 4;
    }
    /**
     *
     * @return
     */
    @Override
    public byte[] serialize() {
        int type = mWriteStatus ? 1 : 0;
        byte[] typeBytes = Ints.toByteArray(type);

        if (mEncryptedPath != null) {
            byte[] pathIDBytes = Longs.toByteArray(mPathID);
            return Bytes.concat(typeBytes, pathIDBytes, mEncryptedPath);
        } else {
            return typeBytes;
        }
    }

    public byte[] serializeAsMessage() {
        byte[] protocolByte = Ints.toByteArray(Constants.SERVER_RESPONSE);

        return Bytes.concat(protocolByte, serialize());
    }
}
