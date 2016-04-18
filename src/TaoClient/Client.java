package TaoClient;

import TaoProxy.Request;

/**
 * Created by ajmagat on 4/12/16.
 */
public interface Client {
    /**
     * @brief
     * @param request
     */
    void sendRequest(Request request);

    /**
     * @brief
     */
    void receiveReply();
}
