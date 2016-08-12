package TaoProxy;

public interface SubtreeBucket extends Bucket {
    void setRight(Bucket b);

    void setLeft(Bucket b);

    /**
     * @brief Accessor method to get the right child of this bucket
     * @return mRight
     */
    SubtreeBucket getRight();

    /**
     * @brief Accessor method to get the left child of this bucket
     * @return mLeft
     */
    SubtreeBucket getLeft();
}
