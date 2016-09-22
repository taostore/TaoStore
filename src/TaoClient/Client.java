package TaoClient;


/**
 * @brief Interface for a TaoStore Client
 */
public interface Client {
    /**
     * @brief Read data from TaoStore
     * @param blockID
     * @return the data in block with block id == blockID
     */
    byte[] read(long blockID);

    /**
     * @brief Write data to block
     * @param blockID
     * @param data
     * @return if write was successful
     */
    boolean write(long blockID, byte[] data);

    void printSubtree();
}
