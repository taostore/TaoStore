package TaoProxy;

import java.util.List;

/**
 * Created by ajmagat on 6/28/16.
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
            if (b != null) {
                mRight = new TaoSubtreeBucket(b, level);
            } else {
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
            if (b != null) {
                mLeft = new TaoSubtreeBucket(b, level);
            } else {
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
        TaoLogger.logForce("Bucket start --------------------");
        TaoLogger.logForce("At level " + mLevel);
        TaoLogger.logForce("Last updated at " + getUpdateTime());
        for (int i = 0; i < bs.size(); i++) {
            TaoLogger.logForce("Block ID: " + bs.get(i).getBlockID());
        }
        TaoLogger.logForce("Bucket end --------------------\n\n");
    }
}
