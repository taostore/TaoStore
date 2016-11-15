package TaoProxy;

import Messages.MessageTypes;
import Messages.ProxyRequest;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.util.Arrays;

/**
 * @brief IImplementation of a class that implements the ProxyRequest message type
 */
public class TaoProxyRequest implements ProxyRequest {
    // The type of response this is
    // 0 is READ
    // 1 is WRITE
    protected int mType;

    // If mType == 0, this is the path that we are interested in reading
    protected long mReadPathID;

    // Amount of bytes in a path
    protected int mPathSize;
    protected byte[] mDataToWrite;
    protected long mTimestamp;

    /**
     * @brief Default constructor
     */
    public TaoProxyRequest() {
        mType = -1;
        mReadPathID = -1;
        mPathSize = -1;
        mTimestamp = 0;
        mDataToWrite = null;
    }

    @Override
    public void initFromSerialized(byte[] serialized) {
        mType = Ints.fromByteArray(Arrays.copyOfRange(serialized, 0, 4));

        if (mType == MessageTypes.PROXY_READ_REQUEST) {
            mReadPathID = Longs.fromByteArray(Arrays.copyOfRange(serialized, 4, 12));
            mPathSize = -1;
            mDataToWrite = null;
        } else if (mType == MessageTypes.PROXY_WRITE_REQUEST || mType == MessageTypes.PROXY_INITIALIZE_REQUEST) {
            // TODO: Change this to not need paths anymore
            mReadPathID = -1;
            mPathSize = Ints.fromByteArray(Arrays.copyOfRange(serialized, 4, 8));
            mTimestamp = Longs.fromByteArray(Arrays.copyOfRange(serialized, 8, 16));


            int serializedIndex = 16;
            int dataToWriteIndex = 0;
            mDataToWrite = new byte[serialized.length - serializedIndex];

            while (serializedIndex < serialized.length) {
                System.arraycopy(serialized, serializedIndex, mDataToWrite, dataToWriteIndex, mPathSize);
                serializedIndex += mPathSize;
                dataToWriteIndex += mPathSize;
            }
        }
    }

    @Override
    public int getType() {
        return mType;
    }

    @Override
    public void setType(int type) {
        mType = type;
    }

    @Override
    public long getTimestamp() {
        return mTimestamp;
    }

    @Override
    public void setTimestamp(long timestamp) {
        mTimestamp = timestamp;
    }

    @Override
    public int getPathSize() {
        return mPathSize;
    }

    @Override
    public void setPathSize(int pathSize) {
        mPathSize = pathSize;
    }

    @Override
    public long getPathID() {
        return mReadPathID;
    }

    @Override
    public void setPathID(long pathID) {
        mReadPathID = pathID;
    }

    @Override
    public byte[] getDataToWrite() {
        return mDataToWrite;
    }

    @Override
    public void setDataToWrite(byte[] data) {
        mDataToWrite = data;
    }

    @Override
    public byte[] serialize() {
        byte[] returnData = null;

        // Serialize based on request type
        if (mType == MessageTypes.PROXY_READ_REQUEST) {
            byte[] typeBytes = Ints.toByteArray(mType);
            byte[] pathBytes = Longs.toByteArray(mReadPathID);

            returnData = Bytes.concat(typeBytes, pathBytes);
        } else if (mType == MessageTypes.PROXY_WRITE_REQUEST || mType == MessageTypes.PROXY_INITIALIZE_REQUEST) {
            byte[] typeBytes = Ints.toByteArray(mType);
            byte[] pathSizeBytes = Ints.toByteArray(mPathSize);
            byte[] timestampBytes = Longs.toByteArray(mTimestamp);

            returnData = Bytes.concat(typeBytes, pathSizeBytes, timestampBytes, mDataToWrite);
        }

        return returnData;
    }
}
