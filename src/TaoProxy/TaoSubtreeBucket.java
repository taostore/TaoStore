package TaoProxy;

import java.util.List;

/**
 * @brief Implementation of a class that implements the SubtreeBucket interface and extends a TaoBucket
 */
public class TaoSubtreeBucket extends TaoBucket implements SubtreeBucket {
    // Left and right child buckets
    private SubtreeBucket mLeft;
    private SubtreeBucket mRight;
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
    public void setRight(Bucket b, int level) {
        if (mRight == null) {
            TaoLogger.log("Subtree bucket is null");
            if (b != null) {
                TaoLogger.log("Subtree bucket is init with b");
                mRight = new TaoSubtreeBucket(b, level);
            } else {
                TaoLogger.log("Subtree bucket is init with null");
                mRight = new TaoSubtreeBucket(level);
            }
        } else {
            if (b == null) {
                mRight = null;
            }
        }
    }

    @Override
    public void setLeft(Bucket b, int level) {
        if (mLeft == null) {
            TaoLogger.log("Subtree bucket is null");
            if (b != null) {
                TaoLogger.log("Subtree bucket is init with b");
                mLeft = new TaoSubtreeBucket(b, level);
            } else {
                TaoLogger.log("Subtree bucket is init with null");
                mLeft = new TaoSubtreeBucket(level);
            }
        } else {
            if (b == null) {
                mLeft = null;
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

    @Override
    public void print() {
        List<Block> bs = getFilledBlocks();
        TaoLogger.log("Bucket start --------------------");
        TaoLogger.log("At level " + mLevel);
        TaoLogger.log("Last updated at " + getUpdateTime());
        for (int i = 0; i < bs.size(); i++) {
            TaoLogger.log("@@@ Block ID: " + bs.get(i).getBlockID());
        }
        TaoLogger.log("Bucket end --------------------\n");
    }
}
