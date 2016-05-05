package TaoProxy;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @brief Class that represents a request from a proxy to the server
 */
public class ProxyRequest {
    public static final int READ = 0;
    public static final int WRITE = 1;

    // 0 = read path
    // 1 = write paths
    private int mType;

    // If mType == 0, this is the path that we are interested in reading
    private long mReadPathID;

    // If mType == 1, this a list of paths that we wish to write to server
    private ArrayList<Path> mPaths;

    /**
     * @brief Default constructor
     */
    public ProxyRequest() {
        mType = -1;
        mReadPathID = -1;
        mPaths = new ArrayList<>();
    }

    /**
     * @brief Constructor for a ProxyRequest of type READ
     * @param type
     * @param pathID
     */
    public ProxyRequest(int type, long pathID) {
        mType = type;
        mReadPathID = pathID;
        mPaths = null;
    }

    /**
     * @brief Constructor for a ProxyRequest of type WRITE
     * @param type
     * @param paths
     */
    public ProxyRequest(int type, ArrayList<Path> paths) {
        mType = type;
        mReadPathID = -1;
        mPaths = paths;
    }

    /**
     * @brief Constructor that takes in an array of bytes to be parsed as a ProxyRequest
     * @param serializedData
     */
    public ProxyRequest(byte[] serializedData) {
        mType = Ints.fromByteArray(Arrays.copyOfRange(serializedData, 0, 4));

        if (mType == ProxyRequest.READ) {
            mReadPathID = Longs.fromByteArray(Arrays.copyOfRange(serializedData, 4, 12));
            mPaths = null;
        } else if (mType == ProxyRequest.WRITE) {
            mReadPathID = -1;
            mPaths = new ArrayList<>();

            int pathSize = Path.getPathSize();
            for (int i = 0; i < Constants.WRITE_BACK_THRESHOLD; i++) {
                mPaths.add(new Path(Arrays.copyOfRange(serializedData, 4 + pathSize * i, 4 + pathSize + pathSize * i)));
            }
        }
    }

    public int getType() {
        return mType;
    }

    public long getPathID() {
        return mReadPathID;
    }

    public List<Path> getPathList() {
        return mPaths;
    }

    public static int getProxyReadRequestSize() {
        return 4 + 8;
    }

    public static int getProxyWriteRequestSize() {
        return 4 + Constants.WRITE_BACK_THRESHOLD * Path.getPathSize();
    }

    /**
     * @brief
     * @return
     */
    public byte[] serialize() {
        byte[] returnData = null;

        if (mType == ProxyRequest.READ) {
            returnData = new byte[ProxyRequest.getProxyReadRequestSize()];
            int metaData = 0;

            byte[] typeBytes = Ints.toByteArray(mType);
            System.arraycopy(typeBytes, 0, returnData, metaData, typeBytes.length);
            metaData += typeBytes.length;

            byte[] pathBytes = Longs.toByteArray(mReadPathID);
            System.arraycopy(pathBytes, 0, returnData, metaData, pathBytes.length);
            metaData += pathBytes.length;
        } else if (mType == ProxyRequest.WRITE) {
            returnData = new byte[ProxyRequest.getProxyWriteRequestSize()];
            int metaData = 0;

            byte[] typeBytes = Ints.toByteArray(mType);
            System.arraycopy(typeBytes, 0, returnData, metaData, typeBytes.length);
            metaData += typeBytes.length;
            int totalSize = metaData;
            int pathSize = Path.getPathSize();
            for (int i = 0; i < Constants.WRITE_BACK_THRESHOLD; i++) {
                totalSize += mPaths.get(i).serialize().length;
                System.arraycopy(mPaths.get(i).serialize(), 0, returnData, metaData + pathSize * i, pathSize);
            }
        }

        return returnData;
    }

    /**
     * @brief
     * @return
     */
    public byte[] serializeAsMessage() {
        byte[] serial = serialize();

        byte[] protocolByte = null;
        if (mType == ProxyRequest.READ) {
            protocolByte = Ints.toByteArray(Constants.PROXY_READ_REQUEST);
        } else if (mType == ProxyRequest.WRITE) {
            protocolByte = Ints.toByteArray(Constants.PROXY_WRITE_REQUEST);
        }

        return Bytes.concat(protocolByte, serial);
    }
}
