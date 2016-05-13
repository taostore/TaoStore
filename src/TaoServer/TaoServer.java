package TaoServer;

import TaoProxy.*;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

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
    RandomAccessFile mDiskFile;

    // The height of the tree
    int mTreeHeight;

    // The total amount of server storage in MB
    long mServerSize;

    // Read-write lock for bucket
    private final transient ReentrantReadWriteLock mRWL = new ReentrantReadWriteLock();

    /**
     * @brief Default constructor
     */
    public TaoServer(long minServerSize) {
        try {
            // Create file object which the server will interact with
            mDiskFile = new RandomAccessFile(ServerConstants.ORAM_FILE, "rws");

            // Calculate the height of the tree
            mTreeHeight = ServerUtility.calculateHeight(minServerSize);

            // Calculate the total amount of space the tree will use
            mServerSize = ServerUtility.calculateSize(mTreeHeight);

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
        return mTreeHeight;
    }

    /**
     * @brief Method which will read the path with the specified pathID from disk and return the result
     * @param pathID
     * @return the bytes of the desired path
     */
    public byte[] readPath(long pathID) {
        // Size of a bucket
        long bucketSize = ServerConstants.BUCKET_SIZE;

        // Array of byte arrays (buckets expressed as byte array)
        byte[][] pathInBytes = new byte[mTreeHeight + 1][];
        try {
            // Acquire read lock
            mRWL.readLock().lock();

            // Get the directions for this path
            boolean[] pathDirection = ServerUtility.getPathFromPID(pathID, mTreeHeight);

            // Variable to represent the offset into the disk file
            long offset = 0;

            // Index into logical array representing the ORAM tree
            long index = 0;

            // The current bucket we are looking for
            int currentBucket = 0;

            // Seek into the file
            mDiskFile.seek(offset);

            // Allocate byte array for this bucket
            pathInBytes[currentBucket] = new byte[(int)bucketSize];

            // Read bytes from the disk file into the byte array for this bucket
            mDiskFile.readFully(pathInBytes[currentBucket]);

            // Increment the current bucket
            currentBucket++;

            // Visit the rest of the buckets
            for (Boolean right : pathDirection) {
                // Navigate the array representing the tree
                if (right) {
                    offset = (2 * index + 2) * bucketSize;
                    index = offset / bucketSize;
                } else {
                    offset = (2 * index + 1) * bucketSize;
                    index = offset / bucketSize;
                }

                // Seek into file
                mDiskFile.seek(offset);

                // Allocate byte array for this bucket
                pathInBytes[currentBucket] = new byte[(int)bucketSize];

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
        // Size of a bucket
        int bucketSize = (int) ServerConstants.BUCKET_SIZE;

        try {
            // Acquire write lock
            mRWL.writeLock().lock();

            // Get the directions for this path
            boolean[] pathDirection = ServerUtility.getPathFromPID(pathID, mTreeHeight);

            // Variable to represent the offset into the disk file
            long offsetInDisk = 0;

            // Index into logical array representing the ORAM tree
            long indexIntoTree = 0;

            // Indices into the data byte array
            int dataIndexStart = 0;
            int dataIndexStop = bucketSize;

            // Seek into the file
            mDiskFile.seek(offsetInDisk);
//            System.out.print("Going to write ");
//            for (byte b : Arrays.copyOfRange(data, dataIndexStart, dataIndexStop)) {
//                System.out.print(b);
//            }
//            System.out.println();

            // Write bucket to disk
            mDiskFile.write(Arrays.copyOfRange(data, dataIndexStart, dataIndexStop));

            // Increment indices
            dataIndexStart += bucketSize;
            dataIndexStop += bucketSize;

            // Write the rest of the buckets
            for (Boolean right : pathDirection) {
                // Navigate the array representing the tree
                if (right) {
                    offsetInDisk = (2 * indexIntoTree + 2) * bucketSize;
                    indexIntoTree = offsetInDisk / bucketSize;
                } else {
                    offsetInDisk = (2 * indexIntoTree + 1) * bucketSize;
                    indexIntoTree = offsetInDisk / bucketSize;
                }

                // Seek into disk
                mDiskFile.seek(offsetInDisk);

                // Write bucket to disk
                mDiskFile.write(Arrays.copyOfRange(data, dataIndexStart, dataIndexStop));

                // Increment indices
                dataIndexStart += bucketSize;
                dataIndexStop += bucketSize;
            }

            // Release the write lock
            mRWL.writeLock().unlock();

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
                    AsynchronousChannelGroup.withFixedThreadPool(Constants.PROXY_THREAD_COUNT, Executors.defaultThreadFactory());

            // Create a channel
            AsynchronousServerSocketChannel channel =
                    AsynchronousServerSocketChannel.open(threadGroup).bind(new InetSocketAddress(12345));

            // Asynchronously wait for incoming connections
            channel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
                @Override
                public void completed(AsynchronousSocketChannel ch, Void att){
                    System.out.println("Hey just accepted a proxy request");
                    // Start listening for other connections
                    channel.accept(null, this);

                    // Create byte buffer to use to read incoming message
                    ByteBuffer byteBuffer = ByteBuffer.allocate( Constants.MAX_BYTE_BUFFER_SERVER );

                    // Asynchronously read message
                    ch.read(byteBuffer, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void attachment) {
                            System.out.println("Hey just read a proxy request");

                            // Flip byte buffer so we can read from it
                            byteBuffer.flip();

                            // Get the bytes from the message that represent what type of message it is
                            byte[] messageTypeBytes = new byte[4];
                            byteBuffer.get(messageTypeBytes);

                            // TODO: decryption of messageTypeBytes
                            // Find out what type of message we have received
                            int messageType = Ints.fromByteArray(messageTypeBytes);

                            // Serve request
                            if (messageType == Constants.PROXY_READ_REQUEST) {
                                // Get bytes for proxy read request
                                byte[] requestBytes = new byte[ProxyRequest.getProxyReadRequestSize()];
                                byteBuffer.get(requestBytes);

                                // TODO: decryption of requestBytes
                                // Create proxy read request from bytes
                                ProxyRequest proxyReq = new ProxyRequest(requestBytes);

                                // Read the request path
                                byte[] returnPathData = readPath(proxyReq.getPathID());

                                // Create path based on bytes
                                Path returnPath = new Path(proxyReq.getPathID(), returnPathData);

                                // Create a server response
                                ServerResponse readResponse = new ServerResponse(returnPath);

                                // TODO: Encrypt the server response?
                                // Send response to proxy
                                ByteBuffer returnMessage = ByteBuffer.wrap(readResponse.serializeAsMessage());
                                //ByteBuffer returnMessage = ByteBuffer.allocate(readResponse.serializeAsMessage().length);
                                //returnMessage.put(readResponse.serializeAsMessage());
                                //returnMessage.flip();
                                System.out.println("Returning message of size " + readResponse.serializeAsMessage().length);
                                //System.out.println("How much is remaining? " + returnMessage.remaining());
                                ch.write(returnMessage);
                            } else if (messageType == Constants.PROXY_WRITE_REQUEST) {
                                // Get bytes for proxy write request
                                byte[] requestBytes = new byte[ProxyRequest.getProxyWriteRequestSize()];
                                byteBuffer.get(requestBytes);

                                // TODO: decryption of requestBytes
                                // Create proxy write request from bytes
                                ProxyRequest proxyReq = new ProxyRequest(requestBytes);

                                // Write each path
                                boolean success = true;
                                for (Path p : proxyReq.getPathList()) {
                                    if (! writePath(p.getID(), p.serializeForDiskWrite())) {
                                        success = false;
                                    }
                                }

                                // Create a server response
                                ServerResponse writeResponse = new ServerResponse();
                                writeResponse.setWriteStatus(success);

                                // TODO: Encrypt the server response?
                                // Send response to proxy
                                ByteBuffer returnMessage = ByteBuffer.wrap(writeResponse.serializeAsMessage());
                                ch.write(returnMessage);
                            }
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
        TaoServer server = new TaoServer(Long.parseLong(args[0]));
        server.run();
    }
}
