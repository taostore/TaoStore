package TaoProxy;

import com.google.common.collect.Multiset;

import java.util.Set;

public interface Subtree {
    void initRoot();

    /**
     * @brief Method to add a path to the subtree
     * @param path
     */
    void addPath(Path path);

    void addPath(Path path, long timestamp);

    /**
     * @brief Method to get requested path from subtree
     * note: shallow copy
     * @param pathID
     * @return path with ID == pathID
     */
    Path getPath(long pathID);

    Path getCopyOfPath(long pathID);

    /**
     * @brief Method to get a bucket in the subtree the contains the specified block with block ID == blockID
     * @param blockID
     * @return bucket that contains block with block id == blockID
     */
    Bucket getBucketWithBlock(long blockID);

    /**
     * @brief Method that will let subtree know that block with block ID == blockID can be found in the provided bucket
     * @param blockID
     * @param bucket
     */
    void mapBlockToBucket(long blockID, Bucket bucket);

    /**
     * @brief Method to delete all nodes in path who have blocks not in the pathReqMultiSet and have not been updated
     *        since minTime
     * @param pathID
     * @param minTime
     * @param pathReqMultiSet
     */
    void deleteNodes(long pathID, long minTime, Set<Long> pathReqMultiSet);

    /**
     * @brief Method to clear out a path
     * @param pathID
     */
    void clearPath(long pathID);

    void printSubtree();
}
