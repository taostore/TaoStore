package ReplicatedStorage.TaoServer;

import Configuration.ArgumentParser;
import Configuration.TaoConfigs;
import Messages.MessageCreator;
import TaoProxy.TaoMessageCreator;
import TaoServer.TaoServer;
import Heartbeats.Node.*;

import java.util.Map;

public class RSTaoServer extends  TaoServer {
    protected HeartbeatNode heartbeatNode;

    public RSTaoServer(MessageCreator messageCreator) {
        super(messageCreator);
    }

    @Override
    public void run() {
        heartbeatNode = new HeartbeatNode();
        Runnable heartbeatProcedure = () -> heartbeatNode.start();
        new Thread(heartbeatProcedure).start();
        super.run();
    }

    public static void main(String[] args) {
        try {
            // Parse any passed in args
            Map<String, String> options = ArgumentParser.parseCommandLineArguments(args);

            // Determine if the user has their own configuration file name, or just use the default
            String configFileName = options.getOrDefault("config_file", TaoConfigs.USER_CONFIG_FILE);
            TaoConfigs.USER_CONFIG_FILE = configFileName;

            // Create server and run
            RSTaoServer server = new RSTaoServer(new TaoMessageCreator());
            server.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
