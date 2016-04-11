package TaoProxy;

public interface Sequencer {
    /**
     * @brief
     * @param req
     */
    void onReceiveRequest(Request req);

    /**
     * @brief
     * @param resp
     */
    void onReceiveResponse(Response resp);

    /**
     * @brief
     * @param req
     */
    void sendRequest(Request req);

    /**
     * @brief
     * @param resp
     */
    void sendResponse(Response resp);

    /**
     * @brief Method to ensure that each reply from server is returned to client in correct order
     */
    void serializationProcedure();
}
