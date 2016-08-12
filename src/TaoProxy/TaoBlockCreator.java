package TaoProxy;

/**
 * Created by ajmagat on 7/6/16.
 */
public class TaoBlockCreator implements PathCreator {
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
