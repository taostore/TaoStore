package Messages;

/**
 * @brief
 */
public interface ProxyResponse {
    void initFromSerialized(byte[] serialized);
    byte[] serialize();

    long getClientRequestID();
    void setClientRequestID(long requestID);

    // TODO: Rename
    byte[] getReturnData();
    void setReturnData(byte[] data);

    boolean getWriteStatus();
    void setWriteStatus(boolean status);
}
