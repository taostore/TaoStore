package TaoProxy;

/**
 * @brief Interface for class that can create a block, bucket, and path
 * TODO: Need better name
 */
public interface PathCreator {
    /**
     * @brief Create an empty block
     * @return an empty block
     */
    Block createBlock();

    /**
     * @brief Create an empty bucket
     * @return an empty bucket
     */
    Bucket createBucket();

    /**
     * @brief Create an empty path
     * @return an empty path
     */
    Path createPath();
}
