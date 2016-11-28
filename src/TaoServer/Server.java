package TaoServer;

/**
 * @brief Interface for a TaoStore server
 */
public interface Server {
    /**
     * @brief Method which will read the path with the specified pathID from storage and return the result
     * @param pathID
     * @return the bytes of the desired path
     */
    byte[] readPath(long pathID);

    /**
     * @brief Method to write data to the specified file
     * @param pathID
     * @param data
     * @return if the write was successful or not
     */
    boolean writePath(long pathID, byte[] data, long timestamp);

    /**
     * @brief Method to run proxy indefinitely for serving proxy
     */
    void run();
}
