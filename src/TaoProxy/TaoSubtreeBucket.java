package TaoProxy;

import java.util.List;

/**
 * @brief Implementation of a class that implements the SubtreeBucket interface and extends a TaoBucket
 */
public class TaoSubtreeBucket extends TaoBucket implements SubtreeBucket {
    // Left and right child buckets
    private SubtreeBucket mLeft;
    private SubtreeBucket mRight;

    // The level this bucket resides, used for debugging
    private int mLevel;

    /**
     * @brief Default constructor
     */
    public TaoSubtreeBucket() {
        super();
        mLeft = null;
        mRight = null;
        mLevel = -1;
    }

    /**
     * @brief Constructor that takes in a level
     * @param level
     */
    public TaoSubtreeBucket(int level) {
        super();
        mLeft = null;
        mRight = null;
        mLevel = level;
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

    /**
     * @brief Constructor that takes in a bucket and level. Used for debugging
     * @param bucket
     * @param level
     */
    public TaoSubtreeBucket(Bucket bucket, int level) {
        super();
        initFromBucket(bucket);
        mLeft = null;
        mRight = null;
        mLevel = level;
    }

    @Override
    public boolean setRight(Bucket b, int level) {
        if (mRight == null) {
            TaoLogger.logDebug("Subtree bucket is null");
            if (b != null) {
                TaoLogger.logDebug("Subtree bucket is init with b");
                mRight = new TaoSubtreeBucket(b, level);
                return true;
            } else {
                TaoLogger.logDebug("Subtree bucket is init with null");
                mRight = null;
                return true;
            }
        } else {
            TaoLogger.logDebug("Subtree bucket is not null");
            if (b == null) {
                mRight = null;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean setLeft(Bucket b, int level) {
        if (mLeft == null) {
            TaoLogger.logDebug("Subtree bucket is null");
            if (b != null) {
                TaoLogger.logDebug("Subtree bucket is init with b");
                mLeft = new TaoSubtreeBucket(b, level);
                return true;
            } else {
                TaoLogger.logDebug("Subtree bucket is init with null");
                mLeft = null;
                return true;
            }
        } else {
            TaoLogger.logDebug("Subtree bucket is not null");
            if (b == null) {
                mLeft = null;
                return true;
            }
        }
        return false;
    }

    @Override
    public SubtreeBucket getRight() {
        return mRight;
    }

    @Override
    public SubtreeBucket getLeft() {
        return mLeft;
    }

    @Override
    public void print() {
        List<Block> bs = getFilledBlocks();
        TaoLogger.logDebug("Bucket start --------------------");
        TaoLogger.logDebug("At level " + mLevel);
        TaoLogger.logDebug("Last updated at " + getUpdateTime());
        for (int i = 0; i < bs.size(); i++) {
            TaoLogger.logDebug("@@@ BlockID: " + bs.get(i).getBlockID());
        }
        TaoLogger.logDebug("Bucket end --------------------\n");
    }
}
