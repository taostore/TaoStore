package TaoProxy;

/**
 * Interface for a class to do cryto related tasks
 */
public interface CryptoUtil {
    /**
     * @brief Encrypt data
     * @param data
     * @return the encrypted data
     */
    byte[] encrypt(byte[] data);

    /**
     * @brief Data previous encrypted by CryptoUtil
     * @param encryptedData
     * @return the decrypted bytes
     */
    byte[] decrypt(byte[] encryptedData);

    /**
     * @brief Encrypt a path
     * @param p
     * @return the encryption of the path
     */
    byte[] encryptPath(Path p);

    /**
     * @brief Create a path from the encrypted data
     * @param data
     * @return the decryption of data as a path
     */
    Path decryptPath(byte[] data);

    /**
     * @brief Get a random path id
     * @return a random path id from ORAM tree
     */
    int getRandomPathID();
}
