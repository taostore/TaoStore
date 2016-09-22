package TaoProxy;

/**
 * @brief Interface to represent a path
 */
public interface Path {
    /**
     * @brief Method to add bucket into first empty level on path
     * @param bucket
     * @return whether or not the adding was successful
     */
    boolean addBucket(Bucket bucket);

    /**
     * @brief Method to insert a bucket into the path at the specified level
     * @param bucket
     * @param level
     */
    void insertBucket(Bucket bucket, int level);

    /**
     * @brief Accessor method to get all buckets in path
     * @return an array of all buckets in path
     */
    Bucket[] getBuckets();

    /**
     * @brief Method to get the bucket at a specified level in path
     * @param level
     * @return
     */
    Bucket getBucket(int level);

    /**
     * @brief Accessor method to get the path ID
     * @return the path ID
     */
    long getPathID();

    /**
     * @brief Mutator method to set the path ID for this path
     * @param pathID
     */
    void setPathID(long pathID);

    /**
     * @brief Get the height of this path
     * @return
     */
    int getPathHeight();

    /**
     * @brief Method to return the serialization of this path
     * @return
     */
    byte[] serialize();

    /**
     * @brief Method to only serialize the buckets contained in path
     * @return
     */
    byte[] serializeBuckets();

    /**
     * @brief Method to initialize a path given the serialization of a path (of the same class)
     * @param serialized
     */
    void initFromSerialized(byte[] serialized);

    /**
     * @brief Lock this path, disallowing others from modifying it
     */
    void lockPath();

    /**
     * @brief Unlock this path, allowing others to try and obtain lock
     */
    void unlockPath();
}
