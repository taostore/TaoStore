package TaoProxy;

import Configuration.TaoConfigs;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Random;

/**
 * @brief Class to handle crypto related activities
 */
public class TaoCryptoUtil implements CryptoUtil {
    // Secret key for this class to use for encryption/decryption
    private SecretKey mSecretKey;

    /**
     * @brief Default constructor
     */
    public TaoCryptoUtil() {
        try {
            // Generate key
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128);
            mSecretKey = keyGen.generateKey();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @brief Construtor that takes in a pre-made secret key
     * @param key
     */
    public TaoCryptoUtil(SecretKey key) {
        mSecretKey = key;
    }

    @Override
    public byte[] encrypt(byte[] data) {
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            SecretKeySpec k = new SecretKeySpec(mSecretKey.getEncoded(), "AES");
            c.init(Cipher.ENCRYPT_MODE, k);
            byte[] encryptedData = c.doFinal(data);
            return Bytes.concat(c.getIV(), encryptedData);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public byte[] decrypt(byte[] encryptedData) {
        try {
            byte[] iv = Arrays.copyOfRange(encryptedData, 0, TaoConfigs.IV_SIZE);
            SecretKeySpec k = new SecretKeySpec(mSecretKey.getEncoded(), "AES");
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            c.init(Cipher.DECRYPT_MODE, k, new IvParameterSpec(iv));
            return c.doFinal(Arrays.copyOfRange(encryptedData, TaoConfigs.IV_SIZE, encryptedData.length));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public byte[] encryptPath(Path p) {
        try {
            // Create the two pieces of unencrypted data, the path ID and the buckets in path
            byte[] idBytes = Longs.toByteArray(p.getPathID());
            byte[] bucketBytes = p.serializeBuckets();

            // Keep track of bucket size
            int bucketSize = TaoConfigs.BUCKET_SIZE;

            // Encrypt the first bucket
            byte[] unencryptedBucket = Arrays.copyOfRange(bucketBytes, 0, bucketSize);
            byte[] encryptedBuckets = encrypt(unencryptedBucket);

            // Encrypt the rest of the buckets
            for (int i = 1; i < p.getPathHeight() + 1; i++) {
                unencryptedBucket = Arrays.copyOfRange(bucketBytes, bucketSize * i , bucketSize + bucketSize * i);
                byte[] temp = encrypt(unencryptedBucket);
                encryptedBuckets = Bytes.concat(encryptedBuckets, temp);
            }

            // Check if we have more than one server, in which case we must remove some of the bytes for the path
            int numServers = TaoConfigs.PARTITION_SERVERS.size();
            if (numServers > 1) {
                // Calculate which is the first bucket in the path that we need to keep in the encryption
                int firstNeededEncryptedBucketStart = ((numServers / 2) - 1) + 1;

                // Keep only the encrypted buckets starting from the first one needed
                encryptedBuckets = Arrays.copyOfRange(encryptedBuckets,
                        (int) (firstNeededEncryptedBucketStart * TaoConfigs.ENCRYPTED_BUCKET_SIZE), encryptedBuckets.length);
            }

            // Return encrypted path
            return Bytes.concat(idBytes, encryptedBuckets);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Path decryptPath(byte[] data) {
        try {
            // Create a path
            long id = Longs.fromByteArray(Arrays.copyOfRange(data, 0, 8));
            Path p = new TaoPath(id);

            // Keep track of the size of an unencrypted bucket and the header that comes with the encrypted path
            int bucketSize = TaoConfigs.BUCKET_SIZE;
            int pathHeader = 8;

            // Calculate the size of a full length encrypted path
            long fullPathSize = TaoConfigs.ENCRYPTED_BUCKET_SIZE * (TaoConfigs.TREE_HEIGHT + 1);

            // The amount of buckets that will need to be added to make data a full sized path
            int numPadBuckets = 0;

            // Pad the front of the path
            if (data.length - 8 < fullPathSize) {
                // The length of data is not as large as would be required for a full path, so we must pad the front
                // of the path with empty buckets
                long difference = fullPathSize - (data.length - 8);
                numPadBuckets = (int) (difference / TaoConfigs.ENCRYPTED_BUCKET_SIZE);

                // Pad the path
                for (int i = 0; i < numPadBuckets; i++) {
                    p.addBucket(new TaoBucket());
                }
            }

            byte[] serializedBucket;
            byte[] decryptedBucket;
            Bucket b;
            for (int i = numPadBuckets; i < TaoConfigs.TREE_HEIGHT + 1; i++) {
                // Get offset into data
                int offset = pathHeader + (i - numPadBuckets) * (int) TaoConfigs.ENCRYPTED_BUCKET_SIZE;

                // Get serialized bucket from data
                serializedBucket = Arrays.copyOfRange(data, offset, (int) TaoConfigs.ENCRYPTED_BUCKET_SIZE + offset);

                // Decrypt the serialization of the bucket
                decryptedBucket = decrypt(serializedBucket);

                // Cut off padding
                decryptedBucket = Arrays.copyOf(decryptedBucket, bucketSize);

                // Add bucket to path
                b = new TaoBucket();
                b.initFromSerialized(decryptedBucket);
                p.addBucket(b);
            }

            return p;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public int getRandomPathID() {
        Random r = new Random();
        return r.nextInt(1 << TaoConfigs.TREE_HEIGHT);
    }
}
