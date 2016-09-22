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
import java.util.Arrays;
import java.util.Scanner;

/**
 * @brief Class to represent a client of TaoStore
 */
public class TaoClient implements Client {
    // The address of the proxy
    private InetSocketAddress mProxyAddress;

    // The address of this client
    private InetSocketAddress mClientAddress;

    // A MessageCreator to create different types of messages to be passed from client, proxy, and server
    private MessageCreator mMessageCreator;

    // Counter to keep track of current request number
    // Incremented after each request
    private static int mRequestID = 0;

    /**
     * @brief Default constructor
     */
    public TaoClient() {
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

    /**
     * @brief Private helper method to send a read or write request to a TaoStore proxy
     * @param type
     * @param blockID
     * @param data
     * @return a ProxyResponse
     */
    private ProxyResponse sendRequest(int type, long blockID, byte[] data) {
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

            // Create client request
            ClientRequest request = mMessageCreator.createClientRequest();

            // Create request id
            // TODO: generate random request ID, or just sequentially increase?
            long requestID = mRequestID;
            mRequestID++;

            // Set data for request
            request.setBlockID(blockID);
            request.setRequestID(requestID);
            request.setClientAddress(mClientAddress);

            // Set additional data depending on message type
            if (type == MessageTypes.CLIENT_READ_REQUEST) {
                request.setType(MessageTypes.CLIENT_READ_REQUEST);
            } else if (type == MessageTypes.CLIENT_WRITE_REQUEST) {
                request.setType(MessageTypes.CLIENT_WRITE_REQUEST);
                request.setData(data);
            }

            // Send request to proxy
            byte[] serializedRequest = request.serialize();
            byte[] header = MessageUtility.createMessageHeaderBytes(request.getType(), serializedRequest.length);
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

    /**
     * @brief Private helper method that will wait for proxy responses
     * @param obj
     * @param proxyResponse
     */
    // TODO: Responses might come out of order, need to handle this
    private void listenForResponse(Object obj, ProxyResponse proxyResponse) {
        // Create runnable to listen for a ProxyResponse
        Runnable r = () -> {
            try {
                // Wait for connection from proxy
                ServerSocket serverSocket = new ServerSocket(mClientAddress.getPort());
                Socket clientServerSocket = serverSocket.accept();

                // Get input
                InputStream input = clientServerSocket.getInputStream();

                // Get header information
                byte[] messageTypeBytes = new byte[4];
                byte[] messageLengthBytes = new byte[4];
                input.read(messageTypeBytes, 0, messageTypeBytes.length);
                input.read(messageLengthBytes);
                int messageType = Ints.fromByteArray(messageTypeBytes);
                int messageLength = Ints.fromByteArray(messageLengthBytes);

                // If the message type if a proxy response, we parse the received data into a ProxyResponse
                if (messageType == MessageTypes.PROXY_RESPONSE) {
                    byte[] responseBytes = new byte[messageLength];
                    input.read(responseBytes);

                    proxyResponse.initFromSerialized(responseBytes);
                }

                // Close all streams
                input.close();
                clientServerSocket.close();
                serverSocket.close();

                // Notify waiting threads
                synchronized (obj) {
                    obj.notifyAll();
                }
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        // Start runnable on new thread
        new Thread(r).start();
    }

    @Override
    public void printSubtree() {
        try {
            // Get proxy name and port
            Socket clientSocket = new Socket(mProxyAddress.getHostName(), mProxyAddress.getPort());

            // Create output stream
            DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());

            // Create client request
            ClientRequest request = mMessageCreator.createClientRequest();
            request.setType(MessageTypes.PRINT_SUBTREE);

            byte[] serializedRequest = request.serialize();
            byte[] header = MessageUtility.createMessageHeaderBytes(request.getType(), serializedRequest.length);
            output.write(header);

            // Close streams and ports
            clientSocket.close();
            output.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args) {
        TaoLogger.logOn = true;
        long systemSize = 246420;
        TaoClient client = new TaoClient();
        Scanner reader = new Scanner(System.in);
        while (true) {
            TaoLogger.log("W for write, R for read, P for print, Q for quit");
            String option = reader.nextLine();

            if (option.equals("Q")) {
                break;
            } else if (option.equals("W")) {
                TaoLogger.log("Enter block ID to write to");
                long blockID = reader.nextLong();

                TaoLogger.log("Enter number to fill in block");
                long fill = reader.nextLong();
                byte[] dataToWrite = new byte[TaoConfigs.BLOCK_SIZE];
                Arrays.fill(dataToWrite, (byte) fill);

                TaoLogger.log("Going to send write request for " + blockID);
                boolean writeStatus = client.write(blockID, dataToWrite);

                if (writeStatus) {
                    TaoLogger.log("Write succeeded");
                } else {
                    TaoLogger.log("Write did not succeed");
                    System.exit(1);
                }
            } else if (option.equals("R")) {
                TaoLogger.log("Enter block ID to read from");

                long blockID = reader.nextLong();

                TaoLogger.log("Going to send read request for " + blockID);
                byte[] result = client.read(blockID);

                TaoLogger.log("The result of the read is a block filled with the number " + result[0]);
            } else if (option.equals("P")) {
                client.printSubtree();
            }
        }

//        // Send write request
//        long blockID = 3;
//        byte[] dataToWrite = new byte[TaoConfigs.BLOCK_SIZE];
//        Arrays.fill(dataToWrite, (byte) blockID);
//        TaoLogger.log("@@@@@@@@@@@@ Going to send write request for " + blockID);
//        boolean writeStatus = client.write(blockID, dataToWrite);
//
//        if (writeStatus) {
//            TaoLogger.log("Write succeeded");
//        } else {
//            TaoLogger.log("Write did not succeed");
//            System.exit(1);
//        }
//
//        blockID = 3;
//        TaoLogger.log("@@@@@@@@@@@@ Going to send read request for " + blockID);
//        byte[] s = client.read(blockID);
//
//        if (Arrays.equals(dataToWrite, s)) {
//            TaoLogger.log("The data was the same");
//        } else {
//            TaoLogger.log("The data was not the same");
//        }
    }
}
