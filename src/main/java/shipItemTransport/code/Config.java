package shipItemTransport.code;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    // Multiblock configuration
    public static boolean SEND_LOGS_TO_ALL_PLAYERS = false;
    public static boolean CREATE_LOG_FILES = false;
    public static boolean CONSOLE_LOGS = true; // Enable by default for debugging

    // Item transport settings
    public static int ITEM_TRANSFER_RATE = 4; // Items per tick
    public static int TRANSFER_RANGE = 16; // Maximum distance for item transfer
    public static boolean ENABLE_ITEM_TRANSPORT = true;

    private static final String CONFIG_FILE_NAME = "ship_item_transporter.toml";
    private static final String DEFAULT_CONFIG =
            "# Ship Item Transporter Configuration\n" +
                    "# Whether to send multiblock logs to all players in chat\n" +
                    "sendLogsToAllPlayers=false\n\n" +

                    "# Whether to create log files in the logs/ directory\n" +
                    "createLogFiles=True\n\n" +

                    "# Whether to output logs to console (useful for debugging)\n" +
                    "consoleLogs=true\n\n" +

                    "# Item transport settings\n" +
                    "# How many items to transfer per tick (20 ticks = 1 second)\n" +
                    "itemTransferRate=4\n\n" +

                    "# Maximum distance for item transfer between blocks in multiblock\n" +
                    "transferRange=16\n\n" +

                    "# Enable or disable item transport functionality\n" +
                    "enableItemTransport=true\n";

    public static void load() {
        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            Path configFile = configDir.resolve(CONFIG_FILE_NAME);

            // Create config file if it doesn't exist
            if (!Files.exists(configFile)) {
                createDefaultConfig(configFile);
            }

            // Read and parse config
            readConfig(configFile);

            // Apply config to other classes
            applyConfig();

            // Use System.out directly since Logger might not be ready yet
            if (CONSOLE_LOGS) {
                System.out.println("[Ship Item Transporter] Config loaded successfully");
            }

        } catch (Exception e) {
            System.out.println("[Ship Item Transporter] Failed to load config: " + e.getMessage());
            applyDefaults();
        }
    }

    private static void createDefaultConfig(Path configFile) throws IOException {
        Files.createDirectories(configFile.getParent());
        Files.write(configFile, DEFAULT_CONFIG.getBytes());
        if (CONSOLE_LOGS) {
            System.out.println("[Ship Item Transporter] Created default config file");
        }
    }

    private static void readConfig(Path configFile) throws IOException {
        String content = new String(Files.readAllBytes(configFile));
        String[] lines = content.split("\n");

        for (String line : lines) {
            line = line.trim();
            // Skip comments and empty lines
            if (line.startsWith("#") || line.isEmpty()) continue;

            String[] parts = line.split("=", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                parseConfigValue(key, value);
            }
        }
    }

    private static void parseConfigValue(String key, String value) {
        try {
            switch (key) {
                case "sendLogsToAllPlayers":
                    SEND_LOGS_TO_ALL_PLAYERS = parseBoolean(value, false);
                    break;
                case "createLogFiles":
                    CREATE_LOG_FILES = parseBoolean(value, false);
                    break;
                case "consoleLogs":
                    CONSOLE_LOGS = parseBoolean(value, true);
                    break;
                case "itemTransferRate":
                    ITEM_TRANSFER_RATE = parseInt(value, 4);
                    break;
                case "transferRange":
                    TRANSFER_RANGE = parseInt(value, 16);
                    break;
                case "enableItemTransport":
                    ENABLE_ITEM_TRANSPORT = parseBoolean(value, true);
                    break;
                default:
                    if (CONSOLE_LOGS) {
                        System.out.println("[Ship Item Transporter] Unknown config option: " + key);
                    }
            }
        } catch (Exception e) {
            if (CONSOLE_LOGS) {
                System.out.println("[Ship Item Transporter] Invalid value for " + key + ": " + value);
            }
        }
    }

    private static int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            if (CONSOLE_LOGS) {
                System.out.println("[Ship Item Transporter] Using default value " + defaultValue + " for " + value);
            }
            return defaultValue;
        }
    }

    private static boolean parseBoolean(String value, boolean defaultValue) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        if (CONSOLE_LOGS) {
            System.out.println("[Ship Item Transporter] Using default value " + defaultValue + " for " + value);
        }
        return defaultValue;
    }

    private static void applyConfig() {
        if (CONSOLE_LOGS) {
            System.out.println("[Ship Item Transporter] Config applied:");
            System.out.println("[Ship Item Transporter] - Player logs: " + SEND_LOGS_TO_ALL_PLAYERS);
            System.out.println("[Ship Item Transporter] - File logs: " + CREATE_LOG_FILES);
            System.out.println("[Ship Item Transporter] - Console logs: " + CONSOLE_LOGS);
            System.out.println("[Ship Item Transporter] - Item transfer rate: " + ITEM_TRANSFER_RATE);
            System.out.println("[Ship Item Transporter] - Transfer range: " + TRANSFER_RANGE);
            System.out.println("[Ship Item Transporter] - Item transport enabled: " + ENABLE_ITEM_TRANSPORT);
        }
    }

    private static void applyDefaults() {
        SEND_LOGS_TO_ALL_PLAYERS = false;
        CREATE_LOG_FILES = false;
        CONSOLE_LOGS = true;
        ITEM_TRANSFER_RATE = 4;
        TRANSFER_RANGE = 16;
        ENABLE_ITEM_TRANSPORT = true;
    }

    public static void reload() {
        load();
    }
}