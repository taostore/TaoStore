package TaoClient;

import TaoProxy.ClientRequest;
import TaoProxy.Constants;
import TaoProxy.ProxyRequest;
import TaoProxy.ProxyResponse;
import com.google.common.primitives.Ints;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @brief Class to represent the client of TaoStore
 */
public class TaoClient implements Client {
    private InetSocketAddress mProxyAddress;
    private InetSocketAddress mClientAddress;
    private static int mRequestID = 0;
    /**
     * @brief Default constructor
     */
    public TaoClient() {
        // TODO: what should be default?
        mProxyAddress = new InetSocketAddress("localhost", 12344);
        mClientAddress = new InetSocketAddress("localhost", 12346);
    }

    /**
     * @brief Constructor that takes in an address for the proxy
     * @param proxyAddress
     * @param proxyPort
     */
    public TaoClient(String proxyAddress, int proxyPort) {
        mProxyAddress = new InetSocketAddress(proxyAddress, proxyPort);
        mClientAddress = new InetSocketAddress("localhost", 12346);
    }

    @Override
    public byte[] read(long blockID) {
        try {
            // Send read request
            ProxyResponse response = sendRequest(ClientRequest.READ, blockID, null);

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
            ProxyResponse response = sendRequest(ClientRequest.WRITE, blockID, data);
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
            ProxyResponse proxyResponse = new ProxyResponse();

            // Listen for a response to this request
            listenForResponse(wait, proxyResponse);

            // Get proxy name and port
            Socket clientSocket = new Socket(mProxyAddress.getHostName(), mProxyAddress.getPort());

            // Create output stream
            DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());

            // Create request
            // TODO: generate random request ID
            long requestID = mRequestID;
            mRequestID++;
            // Create client request
            ClientRequest request = null;
            if (type == ClientRequest.READ) {
                request = new ClientRequest(blockID, ClientRequest.READ, requestID, mClientAddress);
            } else if (type == ClientRequest.WRITE) {
                 request = new ClientRequest(blockID, ClientRequest.WRITE, requestID, data, mClientAddress);
            }

            // Send request to proxy
            output.write(request.serializeAsMessage());

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

                byte[] protocolBytes = new byte[4];
                input.read(protocolBytes, 0, protocolBytes.length);
                int messageType = Ints.fromByteArray(protocolBytes);
                if (messageType == Constants.PROXY_RESPONSE) {
                    byte[] responseBytes = new byte[ProxyResponse.getProxyResponseSize()];
                    input.read(responseBytes);

                    proxyResponse.initialize(responseBytes);
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
