package TaoProxy;

/**
 * @brief Class to hold constants
 */
public class Constants {
    public static final int BUCKET_SIZE = 4;
    public static final int WRITE_BACK_THRESHOLD = 46;
    public static final int BLOCK_META_DATA_SIZE = 8;
    public static final int BLOCK_SIZE = 4096;
    public static final int TOTAL_BLOCK_SIZE = BLOCK_META_DATA_SIZE + BLOCK_SIZE;

    public static final int PROXY_THREAD_COUNT = 10;

    public static final int KEY_SIZE = 128;
    public static final int IV_SIZE = 16;

    // TODO: Make this user inputted data
    // Total data stored in server in MB
    public static final int TOTAL_STORED_DATA = 4186112;

    public static final int TYPE_SIZE = 4;

    // Protocol
    public static final int CLIENT_REQUEST = 0;
    public static final int CLIENT_READ_REQUEST = 98;
    public static final int CLIENT_WRITE_REQUEST = 99;
    public static final int PROXY_READ_REQUEST = 1;
    public static final int PROXY_WRITE_REQUEST = 2;
    public static final int SERVER_RESPONSE = 3;
    public static final int PROXY_RESPONSE = 4;
    public static final int PROXY_INITIALIZE_REQUEST = 5;
}
