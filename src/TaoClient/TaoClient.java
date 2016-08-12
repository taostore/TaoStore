package TaoClient;

import Configuration.TaoConfigs;
import Messages.ClientRequest;
import Messages.MessageCreator;
import Messages.MessageTypes;
import Messages.ProxyResponse;
import TaoProxy.*;
import com.google.common.primitives.Ints;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @brief Class to represent the client of TaoStore
 */
public class TaoClient implements Client {
    private InetSocketAddress mProxyAddress;
    private InetSocketAddress mClientAddress;

    // A MessageCreator to create different types of messages to be passed from client, proxy, and server
    private MessageCreator mMessageCreator;

    private PathCreator mPathCreator;
    private static int mRequestID = 0;
    /**
     * @brief Default constructor
     */
    public TaoClient() {
        // TODO: what should be default?
        mProxyAddress = new InetSocketAddress(TaoConfigs.PROXY_HOSTNAME, TaoConfigs.PROXY_PORT);
        mClientAddress = new InetSocketAddress(TaoConfigs.CLIENT_HOSTNAME, TaoConfigs.CLIENT_PORT);
        mMessageCreator = new TaoMessageCreator();
    }

    /**
     * @brief Constructor that takes in an address for the proxy
     * @param proxyAddress
     * @param proxyPort
     */
    public TaoClient(String proxyAddress, int proxyPort) {
        mProxyAddress = new InetSocketAddress(proxyAddress, proxyPort);
        mClientAddress = new InetSocketAddress(TaoConfigs.CLIENT_HOSTNAME, TaoConfigs.CLIENT_PORT);
        mMessageCreator = new TaoMessageCreator();
    }

    @Override
    public byte[] read(long blockID) {
        try {
            // Send read request
            ProxyResponse response = sendRequest(MessageTypes.CLIENT_READ_REQUEST, blockID, null);

            // Return read data
            return response.getReturnData();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public boolean write(long blockID, byte[] data) {
        try {
            // Send write request
            ProxyResponse response = sendRequest(MessageTypes.CLIENT_WRITE_REQUEST, blockID, data);
            return response.getWriteStatus();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public ProxyResponse sendRequest(int type, long blockID, byte[] data) {
        try {
            // Create object that will be used as a wait condition
            Object wait = new Object();

            // Create an empty response
            ProxyResponse proxyResponse = mMessageCreator.createProxyResponse();

            // Listen for a response to this request
            listenForResponse(wait, proxyResponse);

            // Get proxy name and port
            Socket clientSocket = new Socket(mProxyAddress.getHostName(), mProxyAddress.getPort());

            // Create output stream
            DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());

            // Create request
            // TODO: generate random request ID, or just sequentially increase?
            long requestID = mRequestID;
            mRequestID++;
            ClientRequest request = mMessageCreator.createClientRequest();
            request.setBlockID(blockID);
            request.setRequestID(requestID);
            request.setClientAddress(mClientAddress);

            if (type == MessageTypes.CLIENT_READ_REQUEST) {
                request.setType(MessageTypes.CLIENT_READ_REQUEST);
            } else if (type == MessageTypes.CLIENT_WRITE_REQUEST) {
                request.setType(MessageTypes.CLIENT_WRITE_REQUEST);
                request.setData(data);
            }

            // Send request to proxy
            byte[] serializedRequest = request.serialize();
            TaoLogger.log("Serialized length is " + serializedRequest.length);
            byte[] header = MessageUtility.createMessageHeaderBytes(request.getType(), serializedRequest.length);
            TaoLogger.log("Header length is " + header.length);
            output.write(header);
            output.write(serializedRequest);

            // Close streams and ports
            clientSocket.close();
            output.close();

            // Wait until response
            synchronized (wait) {
                wait.wait();
            }

            // Return proxy response
            return proxyResponse;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public void listenForResponse(Object obj, ProxyResponse proxyResponse) {
        // TODO: Might come out of order
        Runnable r = () -> {
            try {
                ServerSocket serverSocket = new ServerSocket(mClientAddress.getPort());
                Socket clientServerSocket = serverSocket.accept();
                InputStream input = clientServerSocket.getInputStream();

                byte[] messageTypeBytes = new byte[4];
                byte[] messageLengthBytes = new byte[4];
                input.read(messageTypeBytes, 0, messageTypeBytes.length);
                input.read(messageLengthBytes);

                int messageType = Ints.fromByteArray(messageTypeBytes);
                int messageLength = Ints.fromByteArray(messageLengthBytes);

                if (messageType == MessageTypes.PROXY_RESPONSE) {
                    byte[] responseBytes = new byte[messageLength];
                    input.read(responseBytes);

                    proxyResponse.initFromSerialized(responseBytes);
                }

                input.close();
                clientServerSocket.close();
                serverSocket.close();
                synchronized (obj) {
                    obj.notifyAll();
                }
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        new Thread(r).start();
    }

    public static void main(String[] args) {
        TaoClient client = new TaoClient();
    }
}
