package TaoProxy;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.util.Arrays;

/**
 * @brief Class to represent a response for server or proxy
 */
public class ServerResponse {
    // Data for path that this response corresponds to
    private boolean mWriteStatus;
    private long mPathID;
    private byte[] mEncryptedPath;

    /**
     * @brief Default constructor
     */
    public ServerResponse() {
        mWriteStatus = false;
        mPathID = -1;
        mEncryptedPath = null;
    }

    /**
     * @brief Constructor that creates a response for the given request
     * @param pathID
     * @param encryptedData
     */
    public ServerResponse(long pathID, byte[] encryptedData) {
        mWriteStatus = false;
        mPathID = pathID;
        mEncryptedPath = encryptedData;
    }

    public ServerResponse(byte[] serializedData) {
        System.out.println("SERIALIZED DATA SIZE IS " + serializedData.length);
        int type = Ints.fromByteArray(Arrays.copyOfRange(serializedData, 0, 4));
        mWriteStatus = type == 1 ? true : false;
        if (serializedData.length > 4) {
            mPathID = Longs.fromByteArray(Arrays.copyOfRange(serializedData, 4, 12));
            mEncryptedPath = Arrays.copyOfRange(serializedData, 12, serializedData.length);
            System.out.println("THIS NEW ENCRYPTEDPATH HAS SIZE " + mEncryptedPath.length);
        } else {
            mEncryptedPath = null;
        }
    }

    /**
     * @brief
     * @return
     */
    public byte[] getEncryptedPath() {
        return mEncryptedPath;
    }

    public long getPathID() {
        return mPathID;
    }

    public boolean getWriteStatus() {
        return mWriteStatus;
    }

    public void setWriteStatus(boolean status) {
        mWriteStatus = status;
    }

    /**
     *
     * @return
     */
    public static int getServerResponseSize() {
        return Path.getPathSize() + 4;
    }

    public static int getServerResponseSizeWrite() {
        return 4;
    }
    /**
     *
     * @return
     */
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
