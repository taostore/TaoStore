package TaoProxy;

/**
 * @brief Class to represent a response for server or proxy
 */
public class Response {
    // Request that this response is replying to
    private Request mRequest;

    // Data for path that this response corresponds to
    private byte[] mData;

    /**
     * @brief Constructor that creates a response for the given request
     * @param req
     * @param data
     */
    public Response(Request req, byte[] data) {
        mRequest = req;
        mData = data;
    }

    /**
     * @brief Accessor for mRequest
     * @return the request that is response is replying to
     */
    public Request getReq() {
        return mRequest;
    }

    /**
     * @brief Accessor for mData
     * @return the data for the path
     */
    public byte[] getData() {
        return mData;
    }

    // TODO: Create a function to serialize this into bytes to be sent over network
}
