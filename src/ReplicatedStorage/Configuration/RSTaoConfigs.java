package ReplicatedStorage.Configuration;

import Configuration.TaoConfigs;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Additional Configurations for TaoStore replicated storage model
 */
public class RSTaoConfigs {

    // Number of partitions needed to successfully participate in a read path operation
    public static int READ_PATH_QUORUM_SIZE;

    // Number of partitions needed to successfully participate in a write back operation
    public static int WRITE_BACK_QUORUM_SIZE;

    // All serves for all replicas
    public static Map<Integer, List<InetSocketAddress>> ALL_SERVERS;

    // We only want to initialize constants once per run
    public static AtomicBoolean mHasBeenInitialized = new AtomicBoolean();

    /**
     * @brief Initialize configurations that user can set
     */
    public static void initConfiguration() {
        try {
            // Create the configuration file if it doesn't exist
            File configFile = new File(TaoConfigs.USER_CONFIG_FILE);
            configFile.createNewFile();
            InputStream input = new FileInputStream(configFile);

            // Get the default properties
            Properties defaultProp = new Properties();
            InputStream inputDefault = RSTaoConfigs.class.getResourceAsStream("RSTaoConfigs");
            System.out.println(inputDefault == null ? "input default is null" : "not null");
            defaultProp.load(inputDefault);

            // Create the user property file
            Properties properties = new Properties(defaultProp);
            configFile.createNewFile();
            properties.load(input);

            // If we have already initialized the configurations, we don't want to do it again
            if (!mHasBeenInitialized.getAndSet(true)) {
                // Assign number of replicas needed to do a readPath operation
                String read_path_quorum_size = properties.getProperty("read_path_quorum_size");
                READ_PATH_QUORUM_SIZE = Integer.parseInt(read_path_quorum_size);

                // Assign number of replicas needed to do a writeBack operation
                String write_back_quorum_size = properties.getProperty("write_back_quorum_size");
                WRITE_BACK_QUORUM_SIZE = Integer.parseInt(write_back_quorum_size);

                // Make list of all storage servers from all replicas
                String num_storage_replicas = properties.getProperty("num_storage_replicas");
                int num_replicas = Integer.parseInt(num_storage_replicas);
                System.out.println("num replicas: " + num_replicas);

                System.out.println("Initializing servers map");

                ALL_SERVERS = new HashMap<>();

                ALL_SERVERS.put(1, new ArrayList<>(TaoConfigs.PARTITION_SERVERS));
                for (int i = 2; i <= num_replicas; i++) {
                    List<InetSocketAddress> replicaServers = new ArrayList<>();

                    for (int j = 1; j <= TaoConfigs.PARTITION_SERVERS.size(); j++) {
                        String serverName = properties.getProperty("storage_hostname" + Integer.toString(j) + "_replica" + Integer.toString(i));
                        replicaServers.add(new InetSocketAddress(serverName, TaoConfigs.SERVER_PORT));
                    }
                    ALL_SERVERS.put(i, replicaServers);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}
