package TaoProxy;

/**
 * @brief Class to represent a response for server or proxy
 */
public class Response {
    // Request that this response is replying to
    private Request mRequest;

    // Data for path that this response corresponds to
    private Path mPath;

    // Whether or not this is a response for a fake read
    private boolean mFakeRead;


    private long mPathID;

    /**
     * @brief Constructor that creates a response for the given request
     * @param req
     * @param data
     */
    public Response(Request req, byte[] data) {
        mRequest = req;
        // TODO: parse data for blocks
        mPath = null;
        mFakeRead = false;
    }

    /**
     * @brief Accessor for mRequest
     * @return the request that is response is replying to
     */
    public Request getReq() {
        return mRequest;
    }


    public Path getPath() {
        return mPath;
    }

    public boolean isFakeRead() {
        return mFakeRead;
    }

    public void setFakeRead(boolean fake) {
        mFakeRead = fake;
    }

    // TODO: Create a function to serialize this into bytes to be sent over network
}
