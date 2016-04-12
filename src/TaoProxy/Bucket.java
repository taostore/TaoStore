package TaoProxy;

/**
 * @brief Class to represent a bucket
 */
public class Bucket {
    private Block[] mBlocks;

    /**
     * @brief Default constructor
     */
    public Bucket() {
        mBlocks = new Block[Constants.BUCKET_SIZE];
    }

    /**
     * @brief Constructor to initialize bucket given the data of all the blocks
     * @param data
     */
    public Bucket(byte[] data) {
        mBlocks = new Block[Constants.BUCKET_SIZE];

        // TODO: Finish
    }
}
