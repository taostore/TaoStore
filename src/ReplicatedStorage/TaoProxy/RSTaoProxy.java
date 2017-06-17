package ReplicatedStorage.TaoProxy;

import Configuration.ArgumentParser;
import Configuration.TaoConfigs;
import Messages.MessageCreator;
import Messages.MessageTypes;
import Messages.ProxyRequest;
import ReplicatedStorage.Configuration.RSTaoConfigs;
import TaoProxy.*;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class RSTaoProxy extends TaoProxy {

    // Position map which keeps track of what leaf each block corresponds to
    protected RSTaoPositionMap mRSPositionMap;

    /**
     * @brief Constructor
     * @param messageCreator
     * @param pathCreator
     * @param subtree
     */
    public RSTaoProxy(MessageCreator messageCreator, PathCreator pathCreator, Subtree subtree) {
        try {
            // For trace purposes
            TaoLogger.logLevel = TaoLogger.LOG_WARNING;

            // Initialize needed constants
            TaoConfigs.initConfiguration();
            RSTaoConfigs.initConfiguration();

            // Create a CryptoUtil
            mCryptoUtil = new TaoCryptoUtil();

            // Assign subtree
            mSubtree = subtree;

            // Create a position map
            mPositionMap = new TaoPositionMap(TaoConfigs.PARTITION_SERVERS);
            mRSPositionMap = new RSTaoPositionMap(RSTaoConfigs.ALL_SERVERS);

            // Assign the message and path creators
            mMessageCreator = messageCreator;
            mPathCreator = pathCreator;

            // Create a thread pool for asynchronous sockets
            mThreadGroup = AsynchronousChannelGroup.withFixedThreadPool(TaoConfigs.PROXY_THREAD_COUNT, Executors.defaultThreadFactory());

            // Map each leaf to a relative leaf for the servers
            mRelativeLeafMapper = new HashMap<>();
            int numServers = TaoConfigs.PARTITION_SERVERS.size();
            int numLeaves = 1 << TaoConfigs.TREE_HEIGHT;
            int leavesPerPartition = numLeaves / numServers;
            for (int i = 0; i < numLeaves; i += numLeaves/numServers) {
                long j = i;
                long relativeLeaf = 0;
                while (j < i + leavesPerPartition) {
                    mRelativeLeafMapper.put(j, relativeLeaf);
                    j++;
                    relativeLeaf++;
                }
            }

            // Initialize the sequencer and proxy
            mSequencer = new TaoSequencer(mMessageCreator, mPathCreator);
            mProcessor = new RSTaoProcessor(this, mSequencer, mThreadGroup, mMessageCreator, mPathCreator, mCryptoUtil, mSubtree, mPositionMap, mRelativeLeafMapper);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @brief Function to initialize an empty tree on the server side
     */
    public void initializeServer() {
        try {
            // Initialize the top of the subtree
            mSubtree.initRoot();

            // Get the total number of paths
            int totalPaths = 1 << TaoConfigs.TREE_HEIGHT;

            TaoLogger.logInfo("Tree height is " + TaoConfigs.TREE_HEIGHT);
            TaoLogger.logInfo("Total paths " + totalPaths);

            // Variables to both hold the data of a path as well as how big the path is
            byte[] dataToWrite;

            // Create map that will map the addresses of the storage servers to sockets connected to that server
            Map<InetSocketAddress, Socket> mSocketMap = new HashMap<>();

            // Create each connection
            for (List<InetSocketAddress> replica : RSTaoConfigs.ALL_SERVERS.values()) {
                for (InetSocketAddress sa : replica) {
                    System.out.println("Connecting to " + sa);
                    Socket socket = new Socket(sa.getHostName(), sa.getPort());
                    mSocketMap.put(sa, socket);
                }
            }

            // Loop to write each path to server
            for (int i = 0; i < totalPaths; i++) {
                TaoLogger.logForce("Creating path " + i);

                List<InetSocketAddress> sas = mRSPositionMap.getServersForPosition(i);

                for (InetSocketAddress sa : sas) {

                    // Get connection to server, then get input and output streams
                    DataOutputStream output = new DataOutputStream(mSocketMap.get(sa).getOutputStream());
                    InputStream input = mSocketMap.get(sa).getInputStream();

                    // Create empty paths and serialize
                    Path defaultPath = mPathCreator.createPath();
                    defaultPath.setPathID(mRelativeLeafMapper.get(((long) i)));

                    // Encrypt path
                    dataToWrite = mCryptoUtil.encryptPath(defaultPath);

                    // Create a proxy write request
                    ProxyRequest writebackRequest = mMessageCreator.createProxyRequest();
                    writebackRequest.setType(MessageTypes.PROXY_INITIALIZE_REQUEST);
                    writebackRequest.setPathSize(dataToWrite.length);
                    writebackRequest.setDataToWrite(dataToWrite);

                    // Serialize the proxy request
                    byte[] proxyRequest = writebackRequest.serialize();

                    // Send the type and size of message to server
                    byte[] messageTypeBytes = Ints.toByteArray(MessageTypes.PROXY_INITIALIZE_REQUEST);
                    byte[] messageLengthBytes = Ints.toByteArray(proxyRequest.length);
                    output.write(Bytes.concat(messageTypeBytes, messageLengthBytes));

                    // Send actual message to server
                    output.write(proxyRequest);

                    // Read in the response
                    // TODO: Currently not doing anything with response, possibly do something
                    byte[] typeAndSize = new byte[8];
                    input.read(typeAndSize);
                    int type = Ints.fromByteArray(Arrays.copyOfRange(typeAndSize, 0, 4));
                    int length = Ints.fromByteArray(Arrays.copyOfRange(typeAndSize, 4, 8));
                    byte[] message = new byte[length];
                    input.read(message);
                }
            }

            // Close each connection
            for (List<InetSocketAddress> replica : RSTaoConfigs.ALL_SERVERS.values()) {
                for (InetSocketAddress sa : replica) {
                    mSocketMap.get(sa).close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            // Parse any passed in args
            Map<String, String> options = ArgumentParser.parseCommandLineArguments(args);

            // Determine if the user has their own configuration file name, or just use the default
            String configFileName = options.getOrDefault("config_file", TaoConfigs.USER_CONFIG_FILE);
            TaoConfigs.USER_CONFIG_FILE = configFileName;

            // Create proxy
            RSTaoProxy proxy = new RSTaoProxy(new TaoMessageCreator(), new TaoBlockCreator(), new TaoSubtree());

            // Initialize and run server
            proxy.initializeServer();
            TaoLogger.logForce("Finished init, running proxy");
            proxy.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
