package Configuration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ajmagat on 11/15/16.
 */
public class ArgumentParser {
    /**
     * @brief Method to parse command line arguments and return a map of arguments to their values
     * @param args
     * @return
     */
    public static Map<String, String> parseCommandLineArguments(String[] args) {
        // Initialize map
        Map<String, String> argsMap = new HashMap<>();

        // If we should skip the next value
        boolean skipNext = false;

        // Loop through arguments
        for (int i = 0; i < args.length; i++) {
            // Determine if we should skip this current parameter
            if (skipNext) {
                skipNext = false;
                continue;
            }

            String option = args[i];

            if (option.startsWith("--")) {
                skipNext = true;
                if (i + 1 >= args.length) {
                    break;
                }

                argsMap.put(option.substring(2), args[i + 1]);
            }
        }
        return argsMap;
    }
}
