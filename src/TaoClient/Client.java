package TaoClient;


import TaoProxy.ClientRequest;

/**
 * Created by ajmagat on 4/12/16.
 */
public interface Client {
    /**
     * @brief
     * @param blockID
     * @return
     */
    byte[] read(long blockID);

    /**
     * @brief
     * @param blockID
     * @param data
     * @return
     */
    boolean write(long blockID, byte[] data);
}
