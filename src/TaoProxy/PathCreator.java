package TaoProxy;

/**
 * TODO: Need better name
 */
public interface PathCreator {
    Block createBlock();

    Bucket createBucket();

    Path createPath();
}
