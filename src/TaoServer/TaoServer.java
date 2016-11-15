package TaoServer;

import Configuration.TaoConfigs;
import Configuration.Utility;
import Messages.MessageCreator;
import Messages.MessageTypes;
import Messages.ProxyRequest;
import Messages.ServerResponse;
import TaoProxy.*;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @brief Class to represent a server for TaoStore
 */
// TODO: create interface
public class TaoServer {
    // The file object the server will interact with
    protected RandomAccessFile mDiskFile;

    // The total amount of server storage in bytes
    protected long mServerSize;

    // Lock for bucket. Note that we cannot use a read-write lock, as concurrent reading on a RandomAccessFile is not
    // thread safe
    private final transient ReentrantLock mFileLock = new ReentrantLock();

    // A MessageCreator to create different types of messages to be passed from client, proxy, and server
    protected MessageCreator mMessageCreator;

    // The height of the tree stored on this server
    protected int mServerTreeHeight;

    // An array that will represent the tree, and keep track of the most recent timestamp of a particular bucket
    protected long[] mMostRecentTimestamp;

    /**
     * @brief Constructor
     */
    public TaoServer(long minServerSize, MessageCreator messageCreator) {
        try {
            // Trace
            TaoLogger.logLevel = TaoLogger.LOG_OFF;

            // Initialize needed constants
            TaoConfigs.initConfiguration(minServerSize);

            // Calculate the height of the tree that this particular server will store
            mServerTreeHeight = TaoConfigs.STORAGE_SERVER_TREE_HEIGHT;

            // Create array that will keep track of the most recent timestamp of each bucket
            int numPaths = 2 << mServerTreeHeight;
            mMostRecentTimestamp = new long[numPaths];

            // Create file object which the server will interact with
            mDiskFile = new RandomAccessFile(TaoConfigs.ORAM_FILE, "rwd");

            // Assign message creator
            mMessageCreator = messageCreator;

            // Calculate the total amount of space the tree will use
            mServerSize = TaoConfigs.STORAGE_SERVER_SIZE;  //ServerUtility.calculateSize(mServerTreeHeight, TaoConfigs.ENCRYPTED_BUCKET_SIZE);

            // Allocate space
            mDiskFile.setLength(mServerSize);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * @brief Method which will read the path with the specified pathID from disk and return the result
     * @param pathID
     * @return the bytes of the desired path
     */
    public byte[] readPath(long pathID) {
        // Array of byte arrays (buckets expressed as byte array)
        byte[][] pathInBytes = new byte[mServerTreeHeight + 1][];

        try {
            // Acquire the file lock
            mFileLock.lock();

            // Get the directions for this path
            boolean[] pathDirection = Utility.getPathFromPID(pathID, mServerTreeHeight);

            // Variable to represent the offset into the disk file
            long offset = 0;

            // Index into logical array representing the ORAM tree
            long index = 0;

            // The current bucket we are looking for
            int currentBucket = 0;

            // Seek into the file
            mDiskFile.seek(offset);

            // Keep track of bucket size
            int mBucketSize = (int) TaoConfigs.ENCRYPTED_BUCKET_SIZE;

            // Allocate byte array for this bucket
            pathInBytes[currentBucket] = new byte[mBucketSize];

            // Read bytes from the disk file into the byte array for this bucket
            mDiskFile.readFully(pathInBytes[currentBucket]);

            // Increment the current bucket
            currentBucket++;

            // Visit the rest of the buckets
            for (Boolean right : pathDirection) {
                // Navigate the array representing the tree
                if (right) {
                    offset = (2 * index + 2) * mBucketSize;
                    index = offset / mBucketSize;
                } else {
                    offset = (2 * index + 1) * mBucketSize;
                    index = offset / mBucketSize;
                }

                // Seek into file
                mDiskFile.seek(offset);

                // Allocate byte array for this bucket
                pathInBytes[currentBucket] = new byte[mBucketSize];

                // Read bytes from the disk file into the byte array for this bucket
                mDiskFile.readFully(pathInBytes[currentBucket]);

                // Increment the current bucket
                currentBucket++;
            }

            // Release the file lock
            mFileLock.unlock();

            // Put first bucket into a new byte array representing the final return value
            byte[] returnData = pathInBytes[0];

            // Add every bucket into the new byte array
            for (int i = 1; i < pathInBytes.length; i++) {
                returnData = Bytes.concat(returnData, pathInBytes[i]);
            }

            // Return complete path
            returnData = Bytes.concat(Longs.toByteArray(pathID), returnData);
            return returnData;
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Return null if there is an error
        return null;
    }

    /**
     * @brief Method to write data to the specified file
     * @param pathID
     * @param data
     * @return if the write was successful or not
     */
    public boolean writePath(long pathID, byte[] data, long timestamp) {
        try {
            // Acquire the file lock
            mFileLock.lock();

            // Get the directions for this path
            boolean[] pathDirection = Utility.getPathFromPID(pathID, mServerTreeHeight);

            // Variable to represent the offset into the disk file
            long offsetInDisk = 0;

            // Index into logical array representing the ORAM tree
            long indexIntoTree = 0;

            // Indices into the data byte array
            int dataIndexStart = 0;
            int dataIndexStop = (int) TaoConfigs.ENCRYPTED_BUCKET_SIZE;

            // Seek into the file
            mDiskFile.seek(offsetInDisk);

            int timestampIndex = 0;

            // Check to see what the timestamp is for the root
            if (timestamp >= mMostRecentTimestamp[timestampIndex]) {
                // Write bucket to disk
                mDiskFile.write(Arrays.copyOfRange(data, dataIndexStart, dataIndexStop));

                // Update timestamp
                mMostRecentTimestamp[timestampIndex] = timestamp;
            }

            // Keep track of bucket size
            int mBucketSize = (int) TaoConfigs.ENCRYPTED_BUCKET_SIZE;

            // Increment indices
            dataIndexStart += mBucketSize;
            dataIndexStop += mBucketSize;

            // Write the rest of the buckets
            for (Boolean right : pathDirection) {
                // Navigate the array representing the tree
                if (right) {
                    timestampIndex = 2 * timestampIndex + 2;
                    offsetInDisk = (2 * indexIntoTree + 2) * mBucketSize;
                    indexIntoTree = offsetInDisk / mBucketSize;
                } else {
                    timestampIndex = 2 * timestampIndex + 1;
                    offsetInDisk = (2 * indexIntoTree + 1) * mBucketSize;
                    indexIntoTree = offsetInDisk / mBucketSize;
                }

                // Seek into disk
                mDiskFile.seek(offsetInDisk);

                // Get the data for the current bucket to be writen
                byte[] dataToWrite = Arrays.copyOfRange(data, dataIndexStart, dataIndexStop);

                // Check to see that we have the newest version of bucket
                if (timestamp >= mMostRecentTimestamp[timestampIndex]) {
                    // Write bucket to disk
                    mDiskFile.write(dataToWrite);

                    // Update timestamp
                    mMostRecentTimestamp[timestampIndex] = timestamp;
                }

                // Increment indices
                dataIndexStart += mBucketSize;
                dataIndexStop += mBucketSize;
            }

            // Release the file lock
            mFileLock.unlock();

            // Return true, signaling that the write was successful
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Return false, signaling that the write was not successful
        return false;
    }

    /**
     * @brief Method to run proxy indefinitely
     */
    public void run() {
        try {
            // Create a thread pool for asynchronous sockets
            AsynchronousChannelGroup threadGroup =
                    AsynchronousChannelGroup.withFixedThreadPool(TaoConfigs.PROXY_THREAD_COUNT, Executors.defaultThreadFactory());

            // Create a channel
            AsynchronousServerSocketChannel channel =
                    AsynchronousServerSocketChannel.open(threadGroup).bind(new InetSocketAddress(TaoConfigs.SERVER_PORT));

            // Asynchronously wait for incoming connections
            channel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
                @Override
                public void completed(AsynchronousSocketChannel proxyChannel, Void att){
                    // Start listening for other connections
                    channel.accept(null, this);

                    // Start up a new thread to serve this connection
                    Runnable serializeProcedure = () -> serveProxy(proxyChannel);
                    new Thread(serializeProcedure).start();
                }
                @Override
                public void failed(Throwable exc, Void att) {
                    // TODO: Implement?
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @brief Method to serve a proxy connection
     * @param channel
     */
    private void serveProxy(AsynchronousSocketChannel channel) {
        try {
            while (true) {
                // Create byte buffer to use to read incoming message type and size
                ByteBuffer messageTypeAndSize = ByteBuffer.allocate(4 + 4);

                // Read the initial header
                Future initRead = channel.read(messageTypeAndSize);
                initRead.get();

                // Flip buffer for reading
                messageTypeAndSize.flip();

                // Parse the message type and size from server
                byte[] messageTypeBytes = new byte[4];
                byte[] messageLengthBytes = new byte[4];
                messageTypeAndSize.get(messageTypeBytes);
                messageTypeAndSize.get(messageLengthBytes);
                int messageType = Ints.fromByteArray(messageTypeBytes);
                int messageLength = Ints.fromByteArray(messageLengthBytes);

                // Clear buffer
                messageTypeAndSize = null;

                // Read rest of message
                ByteBuffer message = ByteBuffer.allocate(messageLength);
                while (message.remaining() > 0) {
                    Future entireRead = channel.read(message);
                    entireRead.get();
                }

                // Flip buffer for reading
                message.flip();

                // Get bytes from message
                byte[] requestBytes = new byte[messageLength];
                message.get(requestBytes);

                // Clear buffer
                message = null;

                // Create proxy read request from bytes
                ProxyRequest proxyReq = mMessageCreator.parseProxyRequestBytes(requestBytes);

                // To be used for server response
                byte[] messageTypeAndLength = null;
                byte[] serializedResponse = null;

                // Check message type
                if (messageType == MessageTypes.PROXY_READ_REQUEST) {
                    TaoLogger.logForce("Serving a read request");
                    // Read the request path
                    byte[] returnPathData = readPath(proxyReq.getPathID());

                    // Create a server response
                    ServerResponse readResponse = mMessageCreator.createServerResponse();
                    readResponse.setPathID(proxyReq.getPathID());
                    readResponse.setPathBytes(returnPathData);

                    // Create server response data and header
                    serializedResponse = readResponse.serialize();
                    messageTypeAndLength = Bytes.concat(Ints.toByteArray(MessageTypes.SERVER_RESPONSE), Ints.toByteArray(serializedResponse.length));
                } else if (messageType == MessageTypes.PROXY_WRITE_REQUEST) {
                    TaoLogger.logForce("Serving a write request");

                    // If the write was successful
                    boolean success = true;
                    //if (proxyReq.getTimestamp() >= mTimestamp) {
                      //  mTimestamp = proxyReq.getTimestamp();

                        // Get the data to be written
                        byte[] dataToWrite = proxyReq.getDataToWrite();

                        // Get the size of each path
                        int pathSize = proxyReq.getPathSize();

                        // Where to start the current write
                        int startIndex = 0;

                        // Where to end the current write
                        int endIndex = pathSize;

                        // Variables to be used while writing
                        byte[] currentPath;
                        long currentPathID;
                        byte[] encryptedPath;
                        long timestamp = proxyReq.getTimestamp();
                        // Write each path
                        while (startIndex < dataToWrite.length) {
                            // Get the current path and path id from the data to write
                            // TODO: Generalize this somehow, possibly add a method to ProxyRequest
                            currentPath = Arrays.copyOfRange(dataToWrite, startIndex, endIndex);
                            currentPathID = Longs.fromByteArray(Arrays.copyOfRange(currentPath, 0, 8));
                            encryptedPath = Arrays.copyOfRange(currentPath, 8, currentPath.length);

                            // Write path
                            TaoLogger.logForce("Going to writepath " + currentPathID + " with timestamp " + proxyReq.getTimestamp());
                            if (!writePath(currentPathID, encryptedPath, timestamp)) {
                                success = false;
                            }

                            // Increment start and end indexes
                            startIndex += pathSize;
                            endIndex += pathSize;
                        }

                    // Create a server response
                    ServerResponse writeResponse = mMessageCreator.createServerResponse();
                    writeResponse.setIsWrite(success);

                    // Create server response data and header
                    serializedResponse = writeResponse.serialize();
                    messageTypeAndLength = Bytes.concat(Ints.toByteArray(MessageTypes.SERVER_RESPONSE), Ints.toByteArray(serializedResponse.length));
                } else if (messageType == MessageTypes.PROXY_INITIALIZE_REQUEST) {
                    TaoLogger.logForce("Serving an initialize request");
                    if (mMostRecentTimestamp[0] != 0) {
                        mMostRecentTimestamp = new long[2 << mServerTreeHeight];
                    }

                    // If the write was successful
                    boolean success = true;
                    //if (proxyReq.getTimestamp() >= mTimestamp) {
                    //  mTimestamp = proxyReq.getTimestamp();

                    // Get the data to be written
                    byte[] dataToWrite = proxyReq.getDataToWrite();

                    // Get the size of each path
                    int pathSize = proxyReq.getPathSize();

                    // Where to start the current write
                    int startIndex = 0;

                    // Where to end the current write
                    int endIndex = pathSize;

                    // Variables to be used while writing
                    byte[] currentPath;
                    long currentPathID;
                    byte[] encryptedPath;

                    // Write each path
                    while (startIndex < dataToWrite.length) {
                        // Get the current path and path id from the data to write
                        // TODO: Generalize this somehow, possibly add a method to ProxyRequest
                        currentPath = Arrays.copyOfRange(dataToWrite, startIndex, endIndex);
                        currentPathID = Longs.fromByteArray(Arrays.copyOfRange(currentPath, 0, 8));
                        encryptedPath = Arrays.copyOfRange(currentPath, 8, currentPath.length);

                        // Write path
                        TaoLogger.logForce("Going to writepath " + currentPathID + " with timestamp " + proxyReq.getTimestamp());
                        if (!writePath(currentPathID, encryptedPath, 0)) {
                            success = false;
                        }

                        // Increment start and end indexes
                        startIndex += pathSize;
                        endIndex += pathSize;
                    }

                    // Create a server response
                    ServerResponse writeResponse = mMessageCreator.createServerResponse();
                    writeResponse.setIsWrite(success);

                    // Create server response data and header
                    serializedResponse = writeResponse.serialize();
                    messageTypeAndLength = Bytes.concat(Ints.toByteArray(MessageTypes.SERVER_RESPONSE), Ints.toByteArray(serializedResponse.length));
                }

                // Create message to send to proxy
                ByteBuffer returnMessageBuffer = ByteBuffer.wrap(Bytes.concat(messageTypeAndLength, serializedResponse));

                // Write to proxy
                TaoLogger.logForce("Going to send response of size " + serializedResponse.length);
                while (returnMessageBuffer.remaining() > 0) {
                    Future writeToProxy = channel.write(returnMessageBuffer);
                    writeToProxy.get();
                }
                TaoLogger.logForce("Sent response");

                // Clear buffer
                returnMessageBuffer = null;

                // If this was a write request, we break the connection
                if (messageType == MessageTypes.PROXY_WRITE_REQUEST) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Make sure user provides a storage size
        if (args.length != 1) {
            System.out.println("Please provide desired size of storage in MB");
            return;
        }

        // Convert megabytes to bytes
        long inBytes = Long.parseLong(args[0]) * 1024 * 1024;

        // Create server and run
        TaoServer server = new TaoServer(inBytes, new TaoMessageCreator());
        server.run();
    }
}
