package TaoProxy;

/**
 * @brief Class to represent a path
 */
public interface Path {
    void initFromSerialized(byte[] serialized);

    /**
     * @brief Method to add bucket into first empty level on path
     * @param bucket
     * @return
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
     * @return mBuckets
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
     * @return mID
     */
    long getID();

    void setPathID(long pathID);

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
     * @brief
     */
    void lockPath();

    void unlockPath();
}
