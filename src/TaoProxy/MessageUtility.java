package TaoProxy;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import java.nio.ByteBuffer;

/**
 * Created by ajmagat on 6/22/16.
 */
public class MessageUtility {
    public static ByteBuffer createMessageTypeBuffer(int messageType, int messageSize) {
        byte[] messageTypeBytes = Ints.toByteArray(messageType);
        byte[] messageLengthBytes = Ints.toByteArray(messageSize);

        return ByteBuffer.wrap(Bytes.concat(messageTypeBytes, messageLengthBytes));
    }

    public static byte[] createMessageHeaderBytes(int messageType, int messageSize) {
        byte[] messageTypeBytes = Ints.toByteArray(messageType);
        byte[] messageLengthBytes = Ints.toByteArray(messageSize);

        return Bytes.concat(messageTypeBytes, messageLengthBytes);
    }

    public static ByteBuffer createTypeReceiveBuffer() {
        // 4 for type
        // 4 for size
        return ByteBuffer.allocate(4 + 4);
    }

    public static int[] parseTypeAndLength(ByteBuffer b) {
        int[] typeAndLength = new int[2];

        byte[] messageTypeBytes = new byte[4];
        byte[] messageLengthBytes = new byte[4];

        b.get(messageTypeBytes);
        int messageType = Ints.fromByteArray(messageTypeBytes);
        TaoLogger.log("the message type is " + messageType);
        b.get(messageLengthBytes);

      //  int messageType = Ints.fromByteArray(messageTypeBytes);
        int messageLength = Ints.fromByteArray(messageLengthBytes);
        TaoLogger.log("message length is " + messageLength);

        typeAndLength[0] = messageType;
        typeAndLength[1] = messageLength;

        return typeAndLength;
    }
}
