package TaoProxy;


public interface Processor {

    /**
     * @brief Method to read path from server when given a request from sequencer
     * @param req
     */
    void readPath(Request req);

    /**
     * @brief Method to answer the request made by the sequencer
     * @param req
     * @param pathID
     * @param path
     * @param fakeRead
     */
    void answerRequest(Request req, int pathID, Path path, boolean fakeRead);

    /**
     * @brief Flush stash to path
     * @param pathID
     */
    void flush(long pathID);

    /**
     * @brief Write paths from subtree back to server
     * @param timeStamp
     */
    void writeBack(long timeStamp);
}
