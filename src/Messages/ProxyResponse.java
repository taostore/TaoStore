package Messages;

/**
 * @brief Interface the represents the response of a proxy to the client
 */
public interface ProxyResponse {
    /**
     * @brief Initialize ProxyResponse based on a serialized version
     * @param serialized
     */
    void initFromSerialized(byte[] serialized);

    /**
     * @brief Get the client request ID this response is responding to
     * @return client request ID
     */
    long getClientRequestID();

    /**
     * @brief Set the client request ID
     * @param requestID
     */
    void setClientRequestID(long requestID);

    /**
     * @brief Get the data returned from proxy
     * @return data from proxy
     */
    // TODO: Rename
    byte[] getReturnData();

    /**
     * @brief Set the data to be returned
     * @param data
     */
    void setReturnData(byte[] data);

    /**
     * @brief Get the status of a client write request
     * @return write status
     */
    boolean getWriteStatus();

    /**
     * @brief Set the write status
     * @param status
     */
    void setWriteStatus(boolean status);

    /**
     * @brief Method to serialize ProxyResponse into bytes
     * @return byte representation of ProxyResponse
     */
    byte[] serialize();
}
