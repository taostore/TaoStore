package TaoProxy;

public class SubtreeBucket extends Bucket {
    // Left and right child buckets
    private SubtreeBucket mLeft;
    private SubtreeBucket mRight;

    /**
     * @brief Default constructor
     */
    public SubtreeBucket() {
        super();
        mLeft = null;
        mRight = null;
    }

    /**
     * @brief Copy constructor
     * @param bucket
     */
    public SubtreeBucket(Bucket bucket) {
        super(bucket);
        mLeft = null;
        mRight = null;
    }

    /**
     * @brief Mutator method to set right child if it does not exist
     * @param bucket
     */
    public void initializeRight(Bucket bucket) {
        if (mRight == null) {
            if (bucket != null) {
                mRight = new SubtreeBucket(bucket);
            } else {
                mRight = new SubtreeBucket();
            }
        }
    }

    /**
     * @brief Mutator method to set left child if it does not exist
     * @param bucket
     */
    public void initializeLeft(Bucket bucket) {
        if (mLeft == null) {
            if (bucket != null) {
                mLeft = new SubtreeBucket(bucket);
            } else {
                mLeft = new SubtreeBucket();
            }
        }
    }

    /**
     * @brief Accessor method to get the right child of this bucket
     * @return mRight
     */
    public SubtreeBucket getRight() {
        return mRight;
    }

    /**
     * @brief Accessor method to get the left child of this bucket
     * @return mLeft
     */
    public SubtreeBucket getLeft() {
        return mLeft;
    }
}
