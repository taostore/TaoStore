package TaoProxy;

/**
 * @brief Class to hold constants
 */
public class Constants {
    public static final int BUCKET_SIZE = 4;
    public static final int WRITE_BACK_THRESHOLD = 46;
    public static final int BLOCK_META_DATA_SIZE = 8;
    public static final int BLOCK_SIZE = 4096;

    public static final int PROXY_THREAD_COUNT = 10;

    // TODO: Make this user inputted data
    // Total data stored in server in MB
    public static final int TOTAL_STORED_DATA = 4186112;


    public static final int MAX_BYTE_BUFFER_SERVER = ProxyRequest.getProxyWriteRequestSize() + 4;
    public static final int MAX_BYTE_BUFFER_PROXY = ServerResponse.getServerResponseSize() + 4;

    // Protocol
    public static final int CLIENT_REQUEST = 0;
    public static final int PROXY_READ_REQUEST = 1;
    public static final int PROXY_WRITE_REQUEST = 2;
    public static final int SERVER_RESPONSE = 3;
    public static final int PROXY_RESPONSE = 4;
}
