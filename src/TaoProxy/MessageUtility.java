package TaoProxy;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import java.nio.ByteBuffer;

/**
 * A utility class to create common buffer types
 */
public class MessageUtility {
    /**
     * @brief Create a ByteBuffer containing the header of a message
     * @param messageType
     * @param messageSize
     * @return a ByteBuffer that contains the header of a message
     */
    public static ByteBuffer createMessageHeaderBuffer(int messageType, int messageSize) {
        byte[] messageTypeBytes = Ints.toByteArray(messageType);
        byte[] messageLengthBytes = Ints.toByteArray(messageSize);

        return ByteBuffer.wrap(Bytes.concat(messageTypeBytes, messageLengthBytes));
    }

    /**
     * @brief Create a byte array containing the header of a message
     * @param messageType
     * @param messageSize
     * @return a byte array that contains the header of a message
     */
    public static byte[] createMessageHeaderBytes(int messageType, int messageSize) {
        byte[] messageTypeBytes = Ints.toByteArray(messageType);
        byte[] messageLengthBytes = Ints.toByteArray(messageSize);

        return Bytes.concat(messageTypeBytes, messageLengthBytes);
    }

    /**
     * @brief Create a ByteBuffer capable of receiving the header of an incoming message
     * @return an empty ByteBuffer
     */
    public static ByteBuffer createTypeReceiveBuffer() {
        // 4 for type
        // 4 for size
        return ByteBuffer.allocate(4 + 4);
    }

    /**
     * @brief Parse a ByteBuffer and get the header of an incoming message
     * @param b
     * @return an int array containing the values of header parsed from byte array
     */
    public static int[] parseTypeAndLength(ByteBuffer b) {
        int[] typeAndLength = new int[2];

        byte[] messageTypeBytes = new byte[4];
        byte[] messageLengthBytes = new byte[4];

        b.get(messageTypeBytes);
        int messageType = Ints.fromByteArray(messageTypeBytes);

        b.get(messageLengthBytes);
        int messageLength = Ints.fromByteArray(messageLengthBytes);

        typeAndLength[0] = messageType;
        typeAndLength[1] = messageLength;

        return typeAndLength;
    }
}
