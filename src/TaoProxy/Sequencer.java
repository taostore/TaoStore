package TaoProxy;

import Messages.ClientRequest;
import Messages.ServerResponse;

/**
 * @brief Interface for Sequencer
 */
public interface Sequencer {
    /**
     * @brief Handle the receiving of a client request
     * @param req
     */
    void onReceiveRequest(ClientRequest req);

    /**
     * @brief Handle the receiving of a server response
     * @param resp
     */
    void onReceiveResponse(ClientRequest req, ServerResponse resp , byte[] data);

    /**
     * @brief Method to ensure that each reply from server is returned to client in correct order
     */
    void serializationProcedure();
}
