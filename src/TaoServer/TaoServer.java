package TaoServer;

import Configuration.ArgumentParser;
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

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @brief Class to represent a server for TaoStore
 */
public class TaoServer implements Server {

    // The total amount of server storage in bytes
    protected long mServerSize;

    // A MessageCreator to create different types of messages to be passed from client, proxy, and server
    protected MessageCreator mMessageCreator;

    // The height of the tree stored on this server
    protected int mServerTreeHeight;
    // An array that will represent the tree, and keep track of the most recent timestamp of a particular bucket
    protected long[] mMostRecentTimestamp;

    // We will lock at a bucket level when operating on file
    protected ReentrantLock[] mBucketLocks;

    // Max threads for readPath tasks
    public final int READ_PATH_THREADS = 1;
    
    // Max threads for writeBack or initialize tasks
    public final int WRITE_BACK_THREADS = 1;

    // Executor for readPath tasks
    protected ExecutorService mReadPathExecutor;

    // Executor for writeBack and initialize tasks
    protected ExecutorService mWriteBackExecutor;

    // read and write threads will take from a shared pool of file pointers
    private final Semaphore mFilePointersSemaphore = new Semaphore(1);
    
    // shared stack of file pointers
    protected Stack<RandomAccessFile> mFilePointers;

    protected Map<ProxyRequest, Long> mReadStartTimes;
    protected Map<ProxyRequest, Long> mWriteStartTimes;

    /**
     * @brief Constructor
     */
    public TaoServer(MessageCreator messageCreator) {
        // Profiling
        mReadStartTimes = new ConcurrentHashMap<>();
        mWriteStartTimes = new ConcurrentHashMap<>();

        try {
            // Trace
            TaoLogger.logLevel = TaoLogger.LOG_WARNING;

            // No passed in properties file, will use defaults
            TaoConfigs.initConfiguration();

            // Calculate the height of the tree that this particular server will store
            mServerTreeHeight = TaoConfigs.STORAGE_SERVER_TREE_HEIGHT;

            // Create array that will keep track of the most recent timestamp of each bucket and the lock for each bucket
            int numBuckets = (2 << (mServerTreeHeight + 1)) - 1 ;
            mMostRecentTimestamp = new long[numBuckets];
            mBucketLocks = new ReentrantLock[numBuckets];

            // Initialize the locks
            for (int i = 0; i < mBucketLocks.length; i++) {
                mBucketLocks[i] = new ReentrantLock();
            }

            mReadPathExecutor = Executors.newFixedThreadPool(READ_PATH_THREADS);
            mWriteBackExecutor = Executors.newFixedThreadPool(WRITE_BACK_THREADS);

            // Calculate the total amount of space the tree will use
            mServerSize = TaoConfigs.STORAGE_SERVER_SIZE;  //ServerUtility.calculateSize(mServerTreeHeight, TaoConfigs.ENCRYPTED_BUCKET_SIZE);
            
            // Initialize file pointers
            mFilePointers = new Stack<>();
            for (int i = 0; i < WRITE_BACK_THREADS + READ_PATH_THREADS; i++) {
                RandomAccessFile diskFile = new RandomAccessFile(TaoConfigs.ORAM_FILE, "rwd");
                diskFile.setLength(mServerSize);
                mFilePointers.push(diskFile);
            }

            // Assign message creator
            mMessageCreator = messageCreator;
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public byte[] readPath(long pathID) {
        // Array of byte arrays (buckets expressed as byte array)
        byte[][] pathInBytes = new byte[mServerTreeHeight + 1][];

        try {
            // Get the directions for this path
            boolean[] pathDirection = Utility.getPathFromPID(pathID, mServerTreeHeight);

            // Variable to represent the offset into the disk file
            long offset = 0;

            // Index into logical array representing the ORAM tree
            long index = 0;

            // The current bucket we are looking for
            int currentBucket = 0;

            // Keep track of bucket size
            int mBucketSize = (int) TaoConfigs.ENCRYPTED_BUCKET_SIZE;

            // Allocate byte array for this bucket
            pathInBytes[currentBucket] = new byte[mBucketSize];

            // Keep track of the bucket we need to lock
            int bucketLockIndex = 0;
            
            // Grab a file pointer from shared pool
            while (true) {
                try {
                    mFilePointersSemaphore.acquire();
                    break;
                } catch (InterruptedException e) {
                }
            }
            
            RandomAccessFile diskFile = null;
            synchronized(mFilePointers) {
                diskFile = mFilePointers.pop();
            }
            

            // Lock appropriate bucket
            mBucketLocks[bucketLockIndex].lock();

            // Seek into the file
            diskFile.seek(offset);

            // Read bytes from the disk file into the byte array for this bucket
            diskFile.readFully(pathInBytes[currentBucket]);

            // Increment the current bucket
            currentBucket++;

            // Visit the rest of the buckets
            for (Boolean right : pathDirection) {

                int previousBucketLockIndex = bucketLockIndex;

                // Navigate the array representing the tree
                if (right) {
                    bucketLockIndex = 2 * bucketLockIndex + 2;
                    offset = (2 * index + 2) * mBucketSize;
                    index = offset / mBucketSize;
                } else {
                    bucketLockIndex = 2 * bucketLockIndex + 1;
                    offset = (2 * index + 1) * mBucketSize;
                    index = offset / mBucketSize;
                }

                // Allocate byte array for this bucket
                pathInBytes[currentBucket] = new byte[mBucketSize];
                
                // Lock bucket
                mBucketLocks[bucketLockIndex].lock();

                // Unlock previous bucket
                mBucketLocks[previousBucketLockIndex].unlock();               

                // Seek into file
                diskFile.seek(offset);

                // Read bytes from the disk file into the byte array for this bucket
                diskFile.readFully(pathInBytes[currentBucket]);

                // Increment the current bucket
                currentBucket++;
            }
            
            // Unlock final bucket
            mBucketLocks[bucketLockIndex].unlock();

            // Release the file pointer lock
            synchronized(mFilePointers) {
                mFilePointers.push(diskFile);
            }
            mFilePointersSemaphore.release();


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

    @Override
    public boolean writePath(long pathID, byte[] data, long timestamp) {
        try {
            // Get the directions for this path
            boolean[] pathDirection = Utility.getPathFromPID(pathID, mServerTreeHeight);

            // Variable to represent the offset into the disk file
            long offsetInDisk = 0;

            // Index into logical array representing the ORAM tree
            long indexIntoTree = 0;

            // Indices into the data byte array
            int dataIndexStart = 0;
            int dataIndexStop = (int) TaoConfigs.ENCRYPTED_BUCKET_SIZE;

            // The current bucket we are looking for
            int bucketLockIndex = 0;

            // The current timestamp we are checking
            int timestampIndex = 0;
            
            // Grab a file pointer from shared pool
            while (true) {
                try {
                    mFilePointersSemaphore.acquire();
                    break;
                } catch (InterruptedException e) {
                }
            }
            
            RandomAccessFile diskFile = null;
            synchronized(mFilePointers) {
                diskFile = mFilePointers.pop();
            }
            
            // Lock bucket
            mBucketLocks[bucketLockIndex].lock();

            // Check to see what the timestamp is for the root
            if (timestamp >= mMostRecentTimestamp[timestampIndex]) {

                // Seek into file
                diskFile.seek(offsetInDisk);

                // Write bucket to disk
                diskFile.write(Arrays.copyOfRange(data, dataIndexStart, dataIndexStop));

                // Update timestamp
                mMostRecentTimestamp[timestampIndex] = timestamp;

            }

            // Keep track of bucket size
            int mBucketSize = (int) TaoConfigs.ENCRYPTED_BUCKET_SIZE;

            // Increment indices
            dataIndexStart += mBucketSize;
            dataIndexStop += mBucketSize;

            int level = 1;
            
            // Write the rest of the buckets
            for (Boolean right : pathDirection) {

                int previousBucketLockIndex = bucketLockIndex;

                // Navigate the array representing the tree
                if (right) {
                    bucketLockIndex = 2 * bucketLockIndex + 2;
                    timestampIndex = 2 * timestampIndex + 2;
                    offsetInDisk = (2 * indexIntoTree + 2) * mBucketSize;
                    indexIntoTree = offsetInDisk / mBucketSize;
                } else {
                    bucketLockIndex = 2 * bucketLockIndex + 1;
                    timestampIndex = 2 * timestampIndex + 1;
                    offsetInDisk = (2 * indexIntoTree + 1) * mBucketSize;
                    indexIntoTree = offsetInDisk / mBucketSize;
                }

                // Get the data for the current bucket to be writen
                byte[] dataToWrite = Arrays.copyOfRange(data, dataIndexStart, dataIndexStop);

                // Lock bucket
                mBucketLocks[bucketLockIndex].lock();
                
                // Unlock previous bucket
                mBucketLocks[previousBucketLockIndex].unlock();

                // Check to see that we have the newest version of bucket
                if (timestamp >= mMostRecentTimestamp[timestampIndex]) {

                    // Seek into disk
                    diskFile.seek(offsetInDisk);

                    // Write bucket to disk
                    diskFile.write(dataToWrite);

                    // Update timestamp
                    mMostRecentTimestamp[timestampIndex] = timestamp;

                }

                level++;

                // Increment indices
                dataIndexStart += mBucketSize;
                dataIndexStop += mBucketSize;
            }
            
            // Unlock final bucket
            mBucketLocks[bucketLockIndex].unlock();
            
            // Release the file pointer lock
            synchronized(mFilePointers) {
                mFilePointers.push(diskFile);
            }
            mFilePointersSemaphore.release();

            // Return true, signaling that the write was successful
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Return false, signaling that the write was not successful
        return false;
    }

    @Override
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

            // Check message type
            if (messageType == MessageTypes.PROXY_READ_REQUEST) {
                // Profiling
                mReadStartTimes.put(proxyReq, System.currentTimeMillis());

                mReadPathExecutor.submit(() -> {

                    TaoLogger.logDebug("Serving a read request");
                    // Read the request path
                    byte[] returnPathData = readPath(proxyReq.getPathID());

                    long startTime = mReadStartTimes.get(proxyReq);
                    mReadStartTimes.remove(proxyReq);
                    long time = System.currentTimeMillis() - startTime;

                    // Create a server response
                    ServerResponse readResponse = mMessageCreator.createServerResponse();
                    readResponse.setProcessingTime(time);
                    readResponse.setPathID(proxyReq.getPathID());
                    readResponse.setPathBytes(returnPathData);

                    byte[] pathBytes = Longs.toByteArray(proxyReq.getPathID());
                    pathBytes = Bytes.concat(pathBytes, returnPathData);

                    // To be used for server response
                    byte[] messageTypeAndLength = null;
                    byte[] serializedResponse = null;

                    // Create server response data and header
                    serializedResponse = readResponse.serialize();
                    messageTypeAndLength = Bytes.concat(Ints.toByteArray(MessageTypes.SERVER_RESPONSE), Ints.toByteArray(serializedResponse.length));

                    // Create message to send to proxy
                    ByteBuffer returnMessageBuffer = ByteBuffer.wrap(Bytes.concat(messageTypeAndLength, serializedResponse));

                    try {
                        // Write to proxy
                        TaoLogger.logDebug("Going to send response of size " + serializedResponse.length);
                        while (returnMessageBuffer.remaining() > 0) {
                            Future writeToProxy = channel.write(returnMessageBuffer);
                            writeToProxy.get();
                        }
                        TaoLogger.logDebug("Sent response");

                        // Clear buffer
                        returnMessageBuffer = null;

                    } catch (Exception e) {
                        try {
                            channel.close();
                        } catch (IOException e1) {
                        }
                    } finally {
                        // Serve the next proxy request
                        Runnable serializeProcedure = () -> serveProxy(channel);
                        new Thread(serializeProcedure).start();
                    }

                });

            } else if (messageType == MessageTypes.PROXY_WRITE_REQUEST) {
                // Profiling
                mWriteStartTimes.put(proxyReq, System.currentTimeMillis());

                mWriteBackExecutor.submit(()-> {

                    TaoLogger.logDebug("Serving a write request");

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
                        TaoLogger.logDebug("Going to writepath " + currentPathID + " with timestamp " + proxyReq.getTimestamp());
                        if (!writePath(currentPathID, encryptedPath, timestamp)) {
                            success = false;
                        }

                        // Increment start and end indexes
                        startIndex += pathSize;
                        endIndex += pathSize;
                    }

                    long startTime = mWriteStartTimes.get(proxyReq);
                    mWriteStartTimes.remove(proxyReq);
                    long time = System.currentTimeMillis() - startTime;

                    // Create a server response
                    ServerResponse writeResponse = mMessageCreator.createServerResponse();
                    writeResponse.setProcessingTime(time);
                    writeResponse.setIsWrite(success);

                    // To be used for server response
                    byte[] messageTypeAndLength = null;
                    byte[] serializedResponse = null;

                    // Create server response data and header
                    serializedResponse = writeResponse.serialize();
                    messageTypeAndLength = Bytes.concat(Ints.toByteArray(MessageTypes.SERVER_RESPONSE), Ints.toByteArray(serializedResponse.length));
                    // Create message to send to proxy
                    ByteBuffer returnMessageBuffer = ByteBuffer.wrap(Bytes.concat(messageTypeAndLength, serializedResponse));

                    try {
                        // Write to proxy
                        TaoLogger.logDebug("Going to send response of size " + serializedResponse.length);
                        while (returnMessageBuffer.remaining() > 0) {
                            Future writeToProxy = channel.write(returnMessageBuffer);
                            writeToProxy.get();
                        }
                        TaoLogger.logDebug("Sent response");

                        // Clear buffer
                        returnMessageBuffer = null;

                    } catch (Exception e) {
                        try {
                            channel.close();
                        } catch (IOException e1) {
                        }
                    } finally {
                        // Serve the next proxy request
                        Runnable serializeProcedure = () -> serveProxy(channel);
                        new Thread(serializeProcedure).start();
                    }
                });
            } else if (messageType == MessageTypes.PROXY_INITIALIZE_REQUEST) {

                mWriteBackExecutor.submit(()-> {


                    TaoLogger.logDebug("Serving an initialize request");
                    if (mMostRecentTimestamp[0] != 0) {
                        mMostRecentTimestamp = new long[(2 << (mServerTreeHeight + 1)) - 1 ];
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
                        TaoPath path = new TaoPath(currentPathID);
                        path.initFromSerialized(encryptedPath);
                        //path.print();

                        // Write path
                        TaoLogger.logDebug("Going to writepath " + currentPathID + " with timestamp " + proxyReq.getTimestamp());
                        if (!writePath(currentPathID, encryptedPath, 0)) {
                            success = false;
                        }

                        // Increment start and end indexes
                        startIndex += pathSize;
                        endIndex += pathSize;
                    }

                    // To be used for server response
                    byte[] messageTypeAndLength = null;
                    byte[] serializedResponse = null;

                    // Create a server response
                    ServerResponse writeResponse = mMessageCreator.createServerResponse();
                    writeResponse.setIsWrite(success);

                    // Create server response data and header
                    serializedResponse = writeResponse.serialize();
                    messageTypeAndLength = Bytes.concat(Ints.toByteArray(MessageTypes.SERVER_RESPONSE), Ints.toByteArray(serializedResponse.length));

                    // Create message to send to proxy
                    ByteBuffer returnMessageBuffer = ByteBuffer.wrap(Bytes.concat(messageTypeAndLength, serializedResponse));

                    try {
                        // Write to proxy
                        TaoLogger.logDebug("Going to send response of size " + serializedResponse.length);
                        while (returnMessageBuffer.remaining() > 0) {
                            Future writeToProxy = channel.write(returnMessageBuffer);
                            writeToProxy.get();
                        }
                        TaoLogger.logDebug("Sent response");

                        // Clear buffer
                        returnMessageBuffer = null;

                    } catch (Exception e) {
                        try {
                            channel.close();
                        } catch (IOException e1) {
                        }
                    } finally {
                        // Serve the next proxy request
                        Runnable serializeProcedure = () -> serveProxy(channel);
                        new Thread(serializeProcedure).start();
                    }
                });
            }
            
        } catch (Exception e) {
            try {
                channel.close();
            } catch (IOException e1) {
            }
        }
    }

    public static void main(String[] args) {
        try {
            // Parse any passed in args
            Map<String, String> options = ArgumentParser.parseCommandLineArguments(args);

            // Determine if the user has their own configuration file name, or just use the default
            String configFileName = options.getOrDefault("config_file", TaoConfigs.USER_CONFIG_FILE);
            TaoConfigs.USER_CONFIG_FILE = configFileName;

            // Create server and run
            Server server = new TaoServer(new TaoMessageCreator());
            server.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
