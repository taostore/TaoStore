package Configuration;

import TaoProxy.TaoLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @brief Configurations for TaoStore
 */
public class TaoConfigs {
    // Name of file that users can provide to change the below defaults
    public static String USER_CONFIG_FILE = "config.properties";

    // Path of log file directory
    public static String LOG_DIRECTORY;

    // Name of the file each storage server will store information too
    public static String ORAM_FILE;

    // Amount of threads to be used for asynchronous I/O
    public static int PROXY_THREAD_COUNT;

    // The writeback threshold for the proxy
    public static int WRITE_BACK_THRESHOLD;

    // Port to be used by client
    public static int CLIENT_PORT;

    // Port used by proxy
    public static String PROXY_HOSTNAME;
    public static int PROXY_PORT;

    // The storage size of each block in bytes
    public static int BLOCK_SIZE;

    // Amount of blocks that will be put in a bucket
    public static int BLOCKS_IN_BUCKET;

    // The size of the meta information for each block
    // blockID = 8 bytes
    public static int BLOCK_META_DATA_SIZE;

    // The total size in bytes of each block
    public static int TOTAL_BLOCK_SIZE;

    // The size of the initialization vector for encryption
    public static int IV_SIZE;

    // The size of the bucket in bytes, to be determined when initConfiguration is called
    public static int BUCKET_SIZE;

    // The size of a path in bytes, without encryption
    public static int PATH_SIZE;

    // The size of an encrypted bucket, to be determined when initConfiguration is called
    public static long ENCRYPTED_BUCKET_SIZE;

    // The height of the ORAM tree, to be determined when initConfiguration is called
    public static int TREE_HEIGHT;

    // The size of the ORAM tree in bytes, to be determined when initConfiguration is called
    public static long TOTAL_STORAGE_SIZE;

    // The size of each tree that is stored on each server
    public static int STORAGE_SERVER_TREE_HEIGHT;

    // The size of each storage server in bytes
    public static long STORAGE_SERVER_SIZE;

    // Port used by servers
    public static int SERVER_PORT;

    // The list of storage servers to be used by proxy
    public static List<InetSocketAddress> PARTITION_SERVERS = new ArrayList<>();

    // We only want to initialize constants once per run
    public static AtomicBoolean mHasBeenInitialized = new AtomicBoolean();

    /**
     * @brief Initialize configurations that user can set
     */
    public static void initConfiguration() {
        try {
            // Create the configuration file if it doesn't exist
            File configFile = new File(USER_CONFIG_FILE);
            configFile.createNewFile();
            InputStream input = new FileInputStream(configFile);

            // Get the default properties
            Properties defaultProp = new Properties();
            InputStream inputDefault = TaoConfigs.class.getResourceAsStream("TaoDefaultConfigs");
            defaultProp.load(inputDefault);

            // Create the user property file
            Properties properties = new Properties(defaultProp);
            configFile.createNewFile();
            properties.load(input);

            // If we have already initialized the configurations, we don't want to do it again
            if (!mHasBeenInitialized.getAndSet(true)) {

                // Assign the log directory path
                LOG_DIRECTORY = properties.getProperty("log_directory");

                // Assign the file that will be used for ORAM storage
                ORAM_FILE = properties.getProperty("oram_file");

                // Assign how many threads will be used on the proxy
                String proxy_thread_count = properties.getProperty("proxy_thread_count");
                PROXY_THREAD_COUNT = Integer.parseInt(proxy_thread_count);

                // Assign write back threshold
                String write_back_threshold = properties.getProperty("write_back_threshold");
                WRITE_BACK_THRESHOLD = Integer.parseInt(write_back_threshold);

                // Assign client port_name
                String client_port = properties.getProperty("client_port");
                CLIENT_PORT = Integer.parseInt(client_port);

                // Assign proxy hostname
                PROXY_HOSTNAME = properties.getProperty("proxy_hostname");

                // Assign proxy port number
                String proxy_port = properties.getProperty("proxy_port");
                PROXY_PORT = Integer.parseInt(proxy_port);

                // Assign block size without any meta data
                String block_size = properties.getProperty("block_size");
                BLOCK_SIZE = Integer.parseInt(block_size);

                // Assign how many blocks will be in a bucket
                String blocks_in_bucket = properties.getProperty("blocks_in_bucket");
                BLOCKS_IN_BUCKET = Integer.parseInt(blocks_in_bucket);

                // Assign the size of a block's metadata
                String block_meta_data_size = properties.getProperty("block_meta_data_size");
                BLOCK_META_DATA_SIZE = Integer.parseInt(block_meta_data_size);

                // Assign the size of initialization vector to be used for encryption
                String iv_size = properties.getProperty("iv_size");
                IV_SIZE = Integer.parseInt(iv_size);

                // Assign server port number
                String server_port = properties.getProperty("server_port");
                SERVER_PORT = Integer.parseInt(server_port);

                // Make list of all the storage servers
                String num_storage_servers = properties.getProperty("num_storage_servers");
                int num_servers = Integer.parseInt(num_storage_servers);

                PARTITION_SERVERS = new ArrayList<>();
                for (int i = 0; i < num_servers; i++) {
                    String serverName = properties.getProperty("storage_hostname" + Integer.toString(i + 1));
                    PARTITION_SERVERS.add(new InetSocketAddress(serverName, SERVER_PORT));
                }

                // Calculate other configurations based on the above configs as well as the minimum required storage size
                long min_server_size = Long.parseLong(properties.getProperty("min_server_size"));
                initConfiguration(min_server_size * 1024 * 1024);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @brief Private static method to initialize constants based on user defined or default properties
     * @param minServerSize
     */
    private static void initConfiguration(long minServerSize) {
        // First make sure that the amount of storage servers is a power of two
        int numServers = PARTITION_SERVERS.size();
        if ((numServers & -numServers) != numServers) {
            // If not a power of two, we exit
            TaoLogger.logError("The amount of storage servers must be a power of two");
            System.exit(1);
        }

        // Determine the total size of a block based on user configs
        TOTAL_BLOCK_SIZE = BLOCK_META_DATA_SIZE + BLOCK_SIZE;

        // Calculate the size of a bucket based on how big blocks are
        BUCKET_SIZE = calculateBucketSize();

        // Calculate the size of the bucket when padding is added for encryption
        ENCRYPTED_BUCKET_SIZE = calculateEncryptedBucketSize();

        // Calculate the height of the ORAM tree
        TREE_HEIGHT = calculateHeight(minServerSize);

        // Calculate the total size, in bytes, of the ORAM tree
        TOTAL_STORAGE_SIZE = calculateSize(TREE_HEIGHT);

        // Calculate the size of a path in bytes
        PATH_SIZE = calculatePathSize();

        // Calculate the height of each tree on the servers
        STORAGE_SERVER_TREE_HEIGHT = calculateStorageServerHeight(TREE_HEIGHT, numServers);

        // Calculate the size, in bytes, each storage server will store
        STORAGE_SERVER_SIZE = calculateSize(STORAGE_SERVER_TREE_HEIGHT);
    }

    public static void prepareForDemo() {
        PROXY_HOSTNAME = "localhost";
        PARTITION_SERVERS = Arrays.asList(new InetSocketAddress("localhost", SERVER_PORT));
      //  WRITE_BACK_THRESHOLD = 1;
    }

    /**
     * @brief Calculate the size of a bucket given other configurations
     * @return the size of a bucket in bytes
     */
    private static int calculateBucketSize() {
        // Header information for a bucket
        int updateTimeSize = 8;
        int blockBitmapSize = 4;

        // Information about blocks and buckets
        int blocksInBucket = TaoConfigs.BLOCKS_IN_BUCKET;
        int totalBlockSize = TaoConfigs.TOTAL_BLOCK_SIZE;

        // Calculate and return the size of a bucket
        int bucketSize = updateTimeSize + blockBitmapSize + (blocksInBucket * totalBlockSize);
        return bucketSize;
    }

    /**
     * @brief Calculate the size of an encrypted bucket
     * @return the size of an encrypted bucket in bytes
     */
    private static long calculateEncryptedBucketSize() {
        if (TaoConfigs.BUCKET_SIZE % IV_SIZE == 0) {
            // If we don't need padding to get to a multiple of IV_SIZE, we just need to add IV_SIZE to BUCKET_SIZE
            return BUCKET_SIZE + IV_SIZE;
        } else {
            // We need to account for some padding in the encrypted bucket
            long padAmount = IV_SIZE - (TaoConfigs.BUCKET_SIZE % IV_SIZE);

            // Return the BUCKET_SIZE with the IV_SIZE and padding
            return BUCKET_SIZE + padAmount + IV_SIZE;
        }
    }

    /**
     * @brief Calculate the total height of the tree
     * @param storageSize
     * @return the height of the tree
     */
    private static int calculateHeight(long storageSize) {
        // Keep track of the storage size
        long totalTreeSize = storageSize;

        // Pad totalTreeSize so that we have a whole number of blocks
        if ((totalTreeSize % BLOCK_SIZE) != 0) {
            totalTreeSize += BLOCK_SIZE - (totalTreeSize % BLOCK_SIZE);
        }

        // Calculate how many blocks we currently have
        long numBlocks = totalTreeSize / BLOCK_SIZE;

        // Pad the number of blocks so we have a whole number of buckets
        if ((numBlocks % BLOCKS_IN_BUCKET) != 0) {
            numBlocks += BLOCKS_IN_BUCKET - (numBlocks % BLOCKS_IN_BUCKET);
        }

        // Calculate the number of buckets we currently have
        long numBuckets = numBlocks / BLOCKS_IN_BUCKET;

        // Calculate the height of our tree given the number of buckets we have
        return (int) Math.ceil((Math.log(numBuckets + 1) / Math.log(2)) - 1);
    }

    /**
     * @brief Method that will calculate the total storage requirements, in bytes, for the ORAM tree based on the specified
     * height of the tree
     * @param treeHeight
     */
    private static long calculateSize(int treeHeight) {
        // Given the height of tree, we now find the amount of buckets we need to make this a full binary tree
        long numBuckets = (long) Math.pow(2, treeHeight + 1) - 1;

        // We can now calculate the total size of the system
        return numBuckets * ENCRYPTED_BUCKET_SIZE;
    }

    /**
     * @brief Calculate the size of a path in bytes
     * @return the size of the path in bytes
     */
    private static int calculatePathSize() {
        // The ID of the path
        int idSize = 8;
        return idSize + (TaoConfigs.TREE_HEIGHT + 1) * TaoConfigs.BUCKET_SIZE;
    }

    /**
     * @brief Calculate the size of each tree that will be stored on the storage servers
     * @param totalHeight
     * @param numServers
     * @return the size of each storage server trees
     */
    private static int calculateStorageServerHeight(int totalHeight, int numServers) {
        if (numServers > 1) {
            int levelSavedOnProxy = (int) (Math.log(numServers) / Math.log(2));
            return (totalHeight - levelSavedOnProxy);
        }
        return totalHeight;
    }
}
