package TaoProxy;

/**
 * @brief Interface for a SubtreeBucket
 */
public interface SubtreeBucket extends Bucket {
    /**
     * @brief Set the right child of this SubTree bucket
     * @param b
     */
    void setRight(Bucket b, int level);

    /**
     * @brief Set the left child of this Subtree bucket
     * @param b
     */
    void setLeft(Bucket b, int level);

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
