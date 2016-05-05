package TaoProxy;

public interface Sequencer {
    /**
     * @brief
     * @param req
     */
    void onReceiveRequest(ClientRequest req);

    /**
     * @brief
     * @param resp
     */
    void onReceiveResponse(ClientRequest req, ServerResponse resp , byte[] data);

    /**
     * @brief Method to ensure that each reply from server is returned to client in correct order
     */
    void serializationProcedure();
}
