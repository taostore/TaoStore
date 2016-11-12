package Configuration;

import TaoProxy.TaoLogger;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ajmagat on 6/3/16.
 */
public class TaoConfigs {
    // Name of the file each storage server will store information too
    public static final String ORAM_FILE = "oram.txt";

    // Amount of threads to be used for asynchronous I/O
    public static final int PROXY_THREAD_COUNT = 100;

    // The writeback threshold for the proxy
    public static final int WRITE_BACK_THRESHOLD = 40000;

    // Port used by proxy
    public static String PROXY_HOSTNAME = "10.138.0.2";
    public static int PROXY_PORT = 12339;

    // The storage size of each block in bytes
    public static final int BLOCK_SIZE = 4096;

    // Amount of blocks that will be put in a bucket
    public static final int BLOCKS_IN_BUCKET = 4;

    // The size of the meta information for each block
    // blockID = 8 bytes
    public static final int BLOCK_META_DATA_SIZE = 8;

    // The total size in bytes of each block
    public static final int TOTAL_BLOCK_SIZE = BLOCK_META_DATA_SIZE + BLOCK_SIZE;

    // The size of the initialization vector for encryption
    public static final int IV_SIZE = 16;

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
    public static int SERVER_PORT = 12338;

    // The list of storage servers to be used by proxy
    public static final List<InetSocketAddress> PARTITION_SERVERS =
            Arrays.asList(new InetSocketAddress("10.128.0.2", SERVER_PORT),
                    new InetSocketAddress("10.128.0.3", SERVER_PORT),
                    new InetSocketAddress("10.128.0.4", SERVER_PORT),
                    new InetSocketAddress("10.128.0.5", SERVER_PORT));

    /**
     * @brief Static method to initialize constants
     * @param minServerSize
     */
    public static void initConfiguration(long minServerSize) {
        // First make sure that the amount of storage servers is a power of two
        int numServers = PARTITION_SERVERS.size();
        if ((numServers & -numServers) != numServers) {
            // If not a power of two, we exit
            TaoLogger.logError("The amount of storage servers must be a power of two");
            System.exit(1);
        }

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
            int levelSavedOnProxy = (numServers / 2);
            return (totalHeight - levelSavedOnProxy);
        }
        return totalHeight;
    }
}
