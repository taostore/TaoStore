package TaoServer;

/**
 * @brief
 */
public class ServerConstants {
    public static final String ORAM_FILE = "/Users/ajmagat/Desktop/oram.txt";
    public static final long BLOCK_META_DATA_SIZE = 8;
    public static final long BLOCK_SIZE = 4096;
    public static final long TOTAL_BLOCK_SIZE = BLOCK_META_DATA_SIZE + BLOCK_SIZE;
    public static final long NUM_BLOCKS_IN_BUCKET = 4;
    public static final long BUCKET_SIZE = 8 + 4 + NUM_BLOCKS_IN_BUCKET * (TOTAL_BLOCK_SIZE);
}
