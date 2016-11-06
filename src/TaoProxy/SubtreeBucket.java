package TaoProxy;

/**
 * @brief Interface for a SubtreeBucket
 */
public interface SubtreeBucket extends Bucket {
    /**
     * @brief Set the right child of this SubTree bucket
     * @param b
     * @return if something was set or not
     */
    boolean setRight(Bucket b, int level);

    /**
     * @brief Set the left child of this Subtree bucket
     * @param b
     */
    boolean setLeft(Bucket b, int level);

    /**
     * @brief Accessor method to get the right child of this bucket
     * @return the right child of this Subtree bucket
     */
    SubtreeBucket getRight();

    /**
     * @brief Accessor method to get the left child of this bucket
     * @return the left child of this Subtree bucket
     */
    SubtreeBucket getLeft();

    void print();
}
