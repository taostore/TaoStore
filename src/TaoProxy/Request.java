package TaoProxy;

import java.util.Arrays;
import java.util.Objects;

/**
 * @brief A request is of the form (type, block id, data)
 */
public class Request {
    // The types of requests
    public enum RequestType {
        READ,
        WRITE
    }

    // The block ID that this request is asking for
    private long mBlockID;

    // The type of request this is
    private RequestType mType;

    // If mType == WRITE, the data that this request wants to put in a block
    private byte[] mData;

    /**
     * @brief Default constructor
     */
    public Request() {
    }

    /**
     * @brief Accessor for mBlockID
     * @return the block ID for the requested block
     */
    public long getBlockID() {
        return mBlockID;
    }

    /**
     * @brief Accessor for mType
     * @return the type of request
     */
    public RequestType getType() {
        return mType;
    }


    /**
     * @brief Accessor for mData
     * @return the data that was requested to be set on write (null if type is read)
     */
    public byte[] getData() {
        return mData;
    }

    @Override
    public boolean equals(Object obj) {
        if ( ! (obj instanceof Request) ) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        // TODO: Check if this method and hashCode are needed. Currently here due to hash map entry
        // NOTE: This way of checking equivalence isn't necessarily true, modify it if this method and hashCode are needed

        // Two requests are equal if they have the same type, blockID, and data
        Request rhs = (Request) obj;

        if (mType != rhs.getType()) {
            return false;
        }

        if (mBlockID != rhs.getBlockID()) {
            return false;
        }

        if (! Arrays.equals(mData, rhs.getData())) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mBlockID, mType, Arrays.hashCode(mData));
    }
}
