package TaoServer;

import Configuration.TaoConfigs;
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
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @brief Class to represent a server for TaoStore
 */
public class TaoServer {
    // The file object the server will interact with
    private RandomAccessFile mDiskFile;

    // The total amount of server storage in MB
    private long mServerSize;

    // Read-write lock for bucket
    private final transient ReentrantReadWriteLock mRWL = new ReentrantReadWriteLock();

    //
    private MessageCreator mMessageCreator;


    /**
     * @brief Default constructor
     */
    public TaoServer(long minServerSize, MessageCreator messageCreator) {
        try {
            int numServers = TaoConfigs.PARTITION_SERVERS.size();
            if ((numServers & -numServers) != numServers) {
                // TODO: only use a power of two of the servers
            }


            // Create file object which the server will interact with
            mDiskFile = new RandomAccessFile(TaoConfigs.ORAM_FILE, "rws");

            mMessageCreator = messageCreator;

            // Calculate the bucket size, including the IV and padding needed by AES
           // mBucketSize = ServerUtility.calculateBucketSize();

            // Calculate the height of the tree
          //  mTreeHeight = ServerUtility.calculateHeight(minServerSize);

            // Initialize needed constants
            TaoConfigs.initConfiguration(minServerSize);

            // Calculate the total amount of space the tree will use
            mServerSize = ServerUtility.calculateSize(TaoConfigs.TREE_HEIGHT, TaoConfigs.ENCRYPTED_BUCKET_SIZE);

            // Allocate space
            mDiskFile.setLength(mServerSize);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * @brief Accessor for mTreeHeight
     * @return the height of the tree
     */
    public int getHeight() {
        return TaoConfigs.TREE_HEIGHT;
    }

    /**
     * @brief Method which will read the path with the specified pathID from disk and return the result
     * @param pathID
     * @return the bytes of the desired path
     * TODO: add pathid here?
     */
    public byte[] readPath(long pathID) {
        // Array of byte arrays (buckets expressed as byte array)
        byte[][] pathInBytes = new byte[TaoConfigs.TREE_HEIGHT + 1][];
        try {
            // Acquire read lock
            mRWL.readLock().lock();

            // Get the directions for this path
            boolean[] pathDirection = ServerUtility.getPathFromPID(pathID, TaoConfigs.TREE_HEIGHT);

            // Variable to represent the offset into the disk file
            long offset = 0;

            // Index into logical array representing the ORAM tree
            long index = 0;

            // The current bucket we are looking for
            int currentBucket = 0;

            // Seek into the file
            mDiskFile.seek(offset);

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
                    TaoLogger.log("read right");
                    offset = (2 * index + 2) * mBucketSize;
                    index = offset / mBucketSize;
                } else {
                    TaoLogger.log("read left");
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

            // Release the read lock
            mRWL.readLock().unlock();

            // Put first bucket into a new byte array representing the final return value
            byte[] returnData = pathInBytes[0];

            // Add every bucket into the new byte array
            for (int i = 1; i < pathInBytes.length; i++) {
                returnData = Bytes.concat(returnData, pathInBytes[i]);
            }

            returnData = Bytes.concat(Longs.toByteArray(pathID), returnData);
            // Return complete path
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
    public boolean writePath(long pathID, byte[] data) {
        try {
            // Acquire write lock
            mRWL.writeLock().lock();

            // Get the directions for this path
            boolean[] pathDirection = ServerUtility.getPathFromPID(pathID, TaoConfigs.TREE_HEIGHT);

            // Variable to represent the offset into the disk file
            long offsetInDisk = 0;

            // Index into logical array representing the ORAM tree
            long indexIntoTree = 0;

            // Indices into the data byte array
            int dataIndexStart = 0;
            int dataIndexStop = (int) TaoConfigs.ENCRYPTED_BUCKET_SIZE;
            TaoLogger.log("The dataIndexStart is " + dataIndexStart);
            TaoLogger.log("The dataIndexStop is " + dataIndexStop);

            // Seek into the file
            mDiskFile.seek(offsetInDisk);

            // Write bucket to disk
            mDiskFile.write(Arrays.copyOfRange(data, dataIndexStart, dataIndexStop));

            int mBucketSize = (int) TaoConfigs.ENCRYPTED_BUCKET_SIZE;
            // Increment indices
            dataIndexStart += mBucketSize;
            dataIndexStop += mBucketSize;
          //  System.out.println("The dataIndexStart is " + dataIndexStart);
          //  System.out.println("The dataIndexStop is " + dataIndexStop);

            // Write the rest of the buckets
            for (Boolean right : pathDirection) {
                // Navigate the array representing the tree
                if (right) {
                    TaoLogger.log("write right");
                    offsetInDisk = (2 * indexIntoTree + 2) * mBucketSize;
                    indexIntoTree = offsetInDisk / mBucketSize;
                } else {
                    TaoLogger.log("write left");
                    offsetInDisk = (2 * indexIntoTree + 1) * mBucketSize;
                    indexIntoTree = offsetInDisk / mBucketSize;
                }

                // Seek into disk
                mDiskFile.seek(offsetInDisk);

                byte[] dataToWrite = Arrays.copyOfRange(data, dataIndexStart, dataIndexStop);

                // Write bucket to disk
                mDiskFile.write(dataToWrite);

                // Increment indices
                dataIndexStart += mBucketSize;
                dataIndexStop += mBucketSize;

               // System.out.println("The dataIndexStart is " + dataIndexStart);
               // System.out.println("The dataIndexStop is " + dataIndexStop);
            }

            // Release the write lock
            mRWL.writeLock().unlock();

         //   System.exit(1);
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
                public void completed(AsynchronousSocketChannel ch, Void att){

                    // Start listening for other connections
                    channel.accept(null, this);

                    // Create byte buffer to use to read incoming message type and size
                    ByteBuffer messageTypeAndSize = ByteBuffer.allocate(4 + 4);

                    // Asynchronously read message
                    ch.read(messageTypeAndSize, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void attachment) {
                            // Flip buffer for reading
                            messageTypeAndSize.flip();

                            // Parse the message type and size from server
                            byte[] messageTypeBytes = new byte[4];
                            byte[] messageLengthBytes = new byte[4];

                            messageTypeAndSize.get(messageTypeBytes);
                            messageTypeAndSize.get(messageLengthBytes);

                            int messageType = Ints.fromByteArray(messageTypeBytes);
                            int messageLength = Ints.fromByteArray(messageLengthBytes);

                            ByteBuffer message = ByteBuffer.allocate(messageLength);
                            ch.read(message, null, new CompletionHandler<Integer, Void>() {
                                @Override
                                public void completed(Integer result, Void attachment) {

                                    while (message.remaining() > 0) {
                                        ch.read(message, null, this);
                                        return;
                                    }

                                    message.flip();

                                    byte[] requestBytes = new byte[messageLength];
                                    message.get(requestBytes);

                                    // TODO: decryption of requestBytes
                                    // Create proxy read request from bytes
                                    ProxyRequest proxyReq = mMessageCreator.parseProxyRequestBytes(requestBytes);

                                    ByteBuffer messageTypeAndLengthBuffer = null;
                                    byte[] serializedResponse = null;

                                    if (messageType == MessageTypes.PROXY_READ_REQUEST) {
                                        // Read the request path
                                        byte[] returnPathData = readPath(proxyReq.getPathID());

                                        // Create a server response
                                        ServerResponse readResponse = mMessageCreator.createServerResponse();
                                        readResponse.setPathID(proxyReq.getPathID());
                                        readResponse.setPathBytes(returnPathData);

                                        // TODO: Encrypt the server response?
                                        // Send response to proxy
                                        serializedResponse = readResponse.serialize();

                                        byte[] messageTypeBytes = Ints.toByteArray(MessageTypes.SERVER_RESPONSE);
                                        byte[] messageLengthBytes = Ints.toByteArray(serializedResponse.length);

                                        byte[] messageTypeAndLength = Bytes.concat(messageTypeBytes, messageLengthBytes);
                                        messageTypeAndLengthBuffer = ByteBuffer.wrap(messageTypeAndLength);

                                        // First we send the message type to the server along with the size of the message

                                    } else if (messageType == MessageTypes.PROXY_WRITE_REQUEST) {
                                        // Write each path
                                        boolean success = true;
                                        byte[] dataToWrite = proxyReq.getDataToWrite();

                                        int pathSize = proxyReq.getPathSize();
                                        int startIndex = 0;
                                        int endIndex = pathSize;
                                        byte[] currentPath;
                                        long currentPathID;
                                        while (startIndex < dataToWrite.length) {
                                            currentPath = Arrays.copyOfRange(dataToWrite, startIndex, endIndex);
                                            currentPathID = Longs.fromByteArray(Arrays.copyOfRange(currentPath, 0, 8));

                                            startIndex += pathSize;
                                            endIndex += pathSize;
                                            byte[] encryptedPath = Arrays.copyOfRange(currentPath, 8, currentPath.length);

                                            if (! writePath(currentPathID, encryptedPath)) {
                                                success= false;
                                            }
                                        }

                                        // Create a server response
                                        ServerResponse writeResponse = mMessageCreator.createServerResponse();
                                        writeResponse.setIsWrite(success);

                                        // TODO: Encrypt the server response?
                                        // Send response to proxy
                                        serializedResponse = writeResponse.serialize();

                                        // First we send the message type to the server along with the size of the message
                                        byte[] messageTypeBytes = Ints.toByteArray(MessageTypes.SERVER_RESPONSE);
                                        byte[] messageLengthBytes = Ints.toByteArray(serializedResponse.length);

                                        byte[] messageTypeAndLength = Bytes.concat(messageTypeBytes, messageLengthBytes);
                                        messageTypeAndLengthBuffer = ByteBuffer.wrap(messageTypeAndLength);
                                    } else if (messageType == MessageTypes.PROXY_INITIALIZE_REQUEST) {
                                        // Write each path
                                        boolean success = true;
                                        byte[] dataToWrite = proxyReq.getDataToWrite();

                                        int pathSize = proxyReq.getPathSize();
                                        int startIndex = 0;
                                        int endIndex = pathSize;
                                        byte[] currentPath;
                                        long currentPathID;
                                        while (endIndex < dataToWrite.length) {
                                            currentPath = Arrays.copyOfRange(dataToWrite, startIndex, endIndex);
                                            currentPathID = Longs.fromByteArray(Arrays.copyOfRange(currentPath, 0, 8));

                                            startIndex += pathSize;
                                            endIndex += pathSize;

                                            if (! writePath(currentPathID, Arrays.copyOfRange(currentPath, 8, currentPath.length))) {
                                                success= false;
                                            }
                                        }


                                    }

                                    ByteBuffer returnMessageBuffer = ByteBuffer.wrap(serializedResponse);

                                    ch.write(messageTypeAndLengthBuffer, null, new CompletionHandler<Integer, Void>() {

                                        @Override
                                        public void completed(Integer result, Void attachment) {

                                            ch.write(returnMessageBuffer);
                                        }

                                        @Override
                                        public void failed(Throwable exc, Void attachment) {

                                        }
                                    });
                                }

                                @Override
                                public void failed(Throwable exc, Void attachment) {
                                    // TODO: Implement?
                                }
                            });
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            // TODO: Implement?
                        }
                    });
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

    public static void main(String[] args) {
        // Make sure user provides a storage size
        if (args.length != 1) {
            System.out.println("Please provide desired size of storage in MB");
            return;
        }

        // Create server and run
        TaoServer server = new TaoServer(Long.parseLong(args[0]), new TaoMessageCreator());
        server.run();
    }
}
