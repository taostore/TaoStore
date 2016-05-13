package TaoProxy;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import java.util.Arrays;

/**
 * @brief Class to represent a response for server or proxy
 */
public class ServerResponse {
    // Data for path that this response corresponds to
    private Path mPath;
    private boolean mWriteStatus;

    /**
     * @brief Default constructor
     */
    public ServerResponse() {
        mPath = null;
        mWriteStatus = false;
    }

    /**
     * @brief Constructor that creates a response for the given request
     * @param
     * @param path
     */
    public ServerResponse(Path path) {
        mPath = path;
        mWriteStatus = false;
    }

    public ServerResponse(byte[] serializedData) {
        int type = Ints.fromByteArray(Arrays.copyOfRange(serializedData, 0, 4));
        mWriteStatus = type == 1 ? true : false;
        if (serializedData.length > 4) {
            mPath = new Path(Arrays.copyOfRange(serializedData, 4, serializedData.length));
        } else {
            mPath = null;
        }
    }

    /**
     * @brief
     * @return
     */
    public Path getPath() {
        return mPath;
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

        if (mPath != null) {
            return Bytes.concat(typeBytes, mPath.serialize());
        } else {
            return typeBytes;
        }
    }

    public byte[] serializeAsMessage() {
        byte[] protocolByte = Ints.toByteArray(Constants.SERVER_RESPONSE);

        return Bytes.concat(protocolByte, serialize());
    }
}
