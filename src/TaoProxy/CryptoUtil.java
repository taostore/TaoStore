package TaoProxy;

/**
 * Created by ajmagat on 6/2/16.
 */
public interface CryptoUtil {
    byte[] encrypt(byte[] data);

    byte[] decrypt(byte[] encryptedData);

    byte[] encryptPath(Path p);

    Path decryptPath(byte[] data);

    int getRandomPathID();
}
