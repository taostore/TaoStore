package Configuration;

import TaoProxy.Constants;
import TaoProxy.TaoLogger;
import TaoServer.ServerConstants;
import TaoServer.ServerUtility;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ajmagat on 6/3/16.
 */
public class TaoConfigs {

  //  public static final int BUCKET_SIZE = 4;
  //
//    public static final int BLOCK_META_DATA_SIZE = 8;
//    public static final int BLOCK_SIZE = 4096;
//    public static final int TOTAL_BLOCK_SIZE = BLOCK_META_DATA_SIZE + BLOCK_SIZE;
//
//
//
//    public static final int KEY_SIZE = 128;
//    public static final int IV_SIZE = 16;
//
//    // TODO: Make this user inputted data
//    // Total data stored in server in MB
//    public static final int TOTAL_STORED_DATA = 4186112;
//
//
//    public static final int MAX_BYTE_BUFFER_SERVER = ProxyRequest.getProxyWriteRequestSize() + 4;
//    public static final int MAX_BYTE_BUFFER_PROXY = ServerResponse.getServerResponseSize() + 4;
//
//    public static final int TYPE_SIZE = 4;
//
//    // Protocol
//    public static final int CLIENT_REQUEST = 0;
//    public static final int CLIENT_READ_REQUEST = 98;
//    public static final int CLIENT_WRITE_REQUEST = 99;
//    public static final int PROXY_READ_REQUEST = 1;
//    public static final int PROXY_WRITE_REQUEST = 2;
//    public static final int SERVER_RESPONSE = 3;
//    public static final int PROXY_RESPONSE = 4;
//    public static final int PROXY_INITIALIZE_REQUEST = 5;
    public static final String ORAM_FILE = "/Users/ajmagat/Desktop/oram.txt";

    public static final int PROXY_THREAD_COUNT = 10;

    public static final int WRITE_BACK_THRESHOLD = 46;

    public static String CLIENT_HOSTNAME = "localhost";
    public static int CLIENT_PORT = 12337;

    public static String SERVER_HOSTNAME = "localhost";
    public static int SERVER_PORT = 12338;

    public static String PROXY_HOSTNAME = "localhost";
    public static int PROXY_PORT = 12339;


    public static final int BLOCKS_IN_BUCKET = 4;

    public static final int BLOCK_SIZE = 4096;
    public static final int BLOCK_META_DATA_SIZE = 8;
    public static final int TOTAL_BLOCK_SIZE = BLOCK_META_DATA_SIZE + BLOCK_SIZE;

    public static final int KEY_SIZE = 128;
    public static final int IV_SIZE = 16;

    public static int BUCKET_SIZE;
    public static long ENCRYPTED_BUCKET_SIZE;
    // Calculate the size of the ORAM tree in both height and total storage
    public static int TREE_HEIGHT;
    public static long TOTAL_STORAGE_SIZE;

    public static final List<InetSocketAddress> PARTITION_SERVERS = Arrays.asList(new InetSocketAddress("localhost", 12338));
    /**
     * @brief Static method to initialize constants
     * @param minServerSize
     */
    public static void initConfiguration(long minServerSize) {
        // Calculate the size of a bucket based on how big blocks are
        BUCKET_SIZE = calculateBucketSize();

        // Calculate the size of the bucket when padding is added for encryption
        ENCRYPTED_BUCKET_SIZE = calculateEncryptedBucketSize();

        // Calculate the size of the ORAM tree in both height and total storage
        TREE_HEIGHT = calculateHeight(minServerSize);
        TOTAL_STORAGE_SIZE = calculateSize(TREE_HEIGHT, BUCKET_SIZE);
    }

    private static int calculateBucketSize() {
        int updateTimeSize = 8;
        int blockBitmapSize = 4;
       // long initializationVecorSize = Constants.IV_SIZE;
        int blocksInBucket = TaoConfigs.BLOCKS_IN_BUCKET;
        int totalBlockSize = TaoConfigs.TOTAL_BLOCK_SIZE;

        int bucketSize = updateTimeSize + blockBitmapSize + (blocksInBucket * totalBlockSize);

//        if ((bucketSize % Constants.IV_SIZE) != 0) {
//            bucketSize += Constants.IV_SIZE - (bucketSize % Constants.IV_SIZE);
//        }
     //   TaoLogger("bucket size in taoconfigs is " + bucketSize);
        return bucketSize;
    }

    private static long calculateEncryptedBucketSize() {
        if (TaoConfigs.BUCKET_SIZE % 16 == 0) {
            return BUCKET_SIZE;
        } else {
            long padAmount = 16 - (TaoConfigs.BUCKET_SIZE % 16);

            return BUCKET_SIZE + padAmount + IV_SIZE;
        }
    }

    /**
     * @brief Method that will calculate the total storage requirements, in MB, for the ORAM tree based on the specified
     * height of the tree
     * @param treeHeight
     */
    private static long calculateSize(int treeHeight, long bucketSize) {
        // Given the height of tree, we now find the amount of buckets we need to make this a full binary tree
        long numBuckets = (long) Math.pow(2, treeHeight + 1) - 1;

        long newBucketSize = ServerConstants.BUCKET_SIZE;

        if ((newBucketSize % Constants.IV_SIZE) != 0) {
            newBucketSize += Constants.IV_SIZE - (newBucketSize % Constants.IV_SIZE);
        }

        ServerConstants.BUCKET_SIZE = newBucketSize;

        // We can now calculate the total size of the system
        return numBuckets * ServerConstants.BUCKET_SIZE;
    }

    private static int calculateHeight(long storageSize) {
        // Keep track of the storage size
        long totalTreeSize = storageSize;
        long s = totalTreeSize % ServerConstants.TOTAL_BLOCK_SIZE;

        // Pad totalTreeSize so that we have a whole number of blocks
        if ((totalTreeSize % ServerConstants.TOTAL_BLOCK_SIZE) != 0) {
            totalTreeSize += ServerConstants.TOTAL_BLOCK_SIZE - (totalTreeSize % ServerConstants.TOTAL_BLOCK_SIZE);
        }

        // Calculate how many blocks we currently have
        long numBlocks = totalTreeSize / ServerConstants.TOTAL_BLOCK_SIZE;

        // Pad the number of blocks so we have a whole number of buckets
        if ((numBlocks % ServerConstants.NUM_BLOCKS_IN_BUCKET) != 0) {
            numBlocks += ServerConstants.NUM_BLOCKS_IN_BUCKET - (numBlocks % ServerConstants.NUM_BLOCKS_IN_BUCKET);
        }

        // Calculate the number of buckets we currently have
        long numBuckets = numBlocks / ServerConstants.NUM_BLOCKS_IN_BUCKET;

        // Calculate the height of our tree given the number of buckets we have
        return (int) Math.ceil((Math.log(numBuckets + 1) / Math.log(2)) - 1);
    }
}
