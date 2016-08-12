package TaoProxy;

/**
 * Created by ajmagat on 6/28/16.
 */
public class TaoSubtreeBucket extends TaoBucket implements SubtreeBucket {
    // Left and right child buckets
    private SubtreeBucket mLeft;
    private SubtreeBucket mRight;

    /**
     * @brief Default constructor
     */
    public TaoSubtreeBucket() {
        super();
        mLeft = null;
        mRight = null;
    }

    /**
     * @brief Copy constructor
     * @param bucket
     */
    public TaoSubtreeBucket(Bucket bucket) {
        super();
        initFromBucket(bucket);
        mLeft = null;
        mRight = null;
    }

    @Override
    public void setRight(Bucket b) {
        if (mRight == null) {
            if (b != null) {
                mRight = new TaoSubtreeBucket(b);
            } else {
                mRight = new TaoSubtreeBucket();
            }
        }
    }

    @Override
    public void setLeft(Bucket b) {
        if (mLeft == null) {
            if (b != null) {
                mLeft = new TaoSubtreeBucket(b);
            } else {
                mLeft = new TaoSubtreeBucket();
            }
        }
    }

    @Override
    public SubtreeBucket getRight() {
        return mRight;
    }

    @Override
    public SubtreeBucket getLeft() {
        return mLeft;
    }
}
