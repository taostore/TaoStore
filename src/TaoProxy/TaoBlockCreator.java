package TaoProxy;

/**
 * @brief Implementation of a PathCreator to create valid empty blocks, buckets, and paths
 */
public class TaoBlockCreator implements PathCreator {
    /**
     * @brief Default constructor
     */
    public TaoBlockCreator() {
    }

    @Override
    public Block createBlock() {
        return new TaoBlock();
    }

    @Override
    public Bucket createBucket() {
        return new TaoBucket();
    }

    @Override
    public Path createPath() {
        return new TaoPath();
    }
}
