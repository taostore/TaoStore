package TaoProxy;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

/**
 * @brief Class to represent a response for server or proxy
 */
public class ServerResponse {
    // Data for path that this response corresponds to
    private Path mPath;

    /**
     * @brief Default constructor
     */
    public ServerResponse() {
        mPath = null;
    }

    /**
     * @brief Constructor that creates a response for the given request
     * @param
     * @param path
     */
    public ServerResponse(Path path) {
        mPath = path;
    }

    public ServerResponse(byte[] serializedData) {
        mPath = new Path(serializedData);
    }

    /**
     * @brief
     * @return
     */
    public Path getPath() {
        return mPath;
    }

    /**
     *
     * @return
     */
    public static int getServerResponseSize() {
        return Path.getPathSize();
    }

    /**
     *
     * @return
     */
    public byte[] serialize() {
        byte[] returnData = new byte[ServerResponse.getServerResponseSize()];

        System.arraycopy(mPath.serialize(), 0, returnData, 0, mPath.serialize().length);

        return returnData;
    }

    public byte[] serializeAsMessage() {
        byte[] protocolByte = Ints.toByteArray(Constants.SERVER_RESPONSE);

        return Bytes.concat(protocolByte, serialize());

    }
}
