package shipItemTransport.code;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class Logger {
    public static boolean newFile = false;
    public static int currentFileNumber = 1;
    private static PrintWriter currentWriter = null;
    private static final String LOG_FOLDER = "logs";
    private static final String FILE_PREFIX = "ship-item-transporter-";
    private static final String FILE_EXTENSION = ".log";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern FILE_NUMBER_PATTERN = Pattern.compile(FILE_PREFIX + "(\\d+)" + FILE_EXTENSION);

    static {
        initializeLogger();
    }

    private static void initializeLogger() {
        try {
            if (Config.CREATE_LOG_FILES) {
                Files.createDirectories(Paths.get(LOG_FOLDER));
                currentFileNumber = findHighestFileNumber();
                openCurrentLogFile();
            } else if (Config.CONSOLE_LOGS) {
                System.out.println("[Ship Item Transporter] File logging is disabled in config");
            }
        } catch (Exception e) {
            System.err.println("[Ship Item Transporter] Failed to initialize Logger: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int findHighestFileNumber() {
        try {
            return Files.list(Paths.get(LOG_FOLDER))
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .map(fileName -> {
                        Matcher matcher = FILE_NUMBER_PATTERN.matcher(fileName);
                        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
                    })
                    .max(Comparator.naturalOrder())
                    .orElse(0);
        } catch (Exception e) {
            if (Config.CONSOLE_LOGS) {
                System.err.println("[Ship Item Transporter] Error scanning log files: " + e.getMessage());
            }
            return 0;
        }
    }

    private static void openCurrentLogFile() {
        try {
            if (currentWriter != null) {
                currentWriter.close();
            }
            String filename = FILE_PREFIX + currentFileNumber + FILE_EXTENSION;
            File logFile = new File(LOG_FOLDER, filename);
            currentWriter = new PrintWriter(new FileWriter(logFile, true));
        } catch (Exception e) {
            if (Config.CONSOLE_LOGS) {
                System.err.println("[Ship Item Transporter] Failed to open log file: " + e.getMessage());
            }
        }
    }

    public static void sendMessage(String message, boolean outputToPlayers) {
        if (newFile && Config.CREATE_LOG_FILES) {
            currentFileNumber++;
            openCurrentLogFile();
            newFile = false;
        }

        if (Config.CREATE_LOG_FILES) {
            writeToLogFile(message);
        }

        if (outputToPlayers && Config.SEND_LOGS_TO_ALL_PLAYERS) {
            sendToAllPlayers(message);
        }

        if (Config.CONSOLE_LOGS) {
            System.out.println("[Ship Item Transporter] " + message);
        }
    }

    private static void writeToLogFile(String message) {
        if (currentWriter != null && Config.CREATE_LOG_FILES) {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            currentWriter.println("[" + timestamp + "] " + message);
            currentWriter.flush();
        }
    }

    private static void sendToAllPlayers(String message) {
        try {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (ServerLevel level : server.getAllLevels()) {
                    for (ServerPlayer player : level.players()) {
                        try {
                            player.sendSystemMessage(Component.literal("[Ship Transport] " + message));
                        } catch (Exception e) {
                            if (Config.CONSOLE_LOGS) {
                                System.out.println("[Ship Item Transporter] Failed to send message to player: " + e.getMessage());
                            }
                        }
                    }
                }
                if (Config.CONSOLE_LOGS) {
                    System.out.println("[Ship Item Transporter] [PLAYER-MSG] " + message);
                }
            }
        } catch (Exception e) {
            if (Config.CONSOLE_LOGS) {
                System.out.println("[Ship Item Transporter] [PLAYER-MSG-ERROR] " + message);
            }
        }
    }

    public static void close() {
        if (currentWriter != null) {
            currentWriter.close();
            currentWriter = null;
        }
    }

    public static void startNewLogFile() {
        newFile = true;
    }

    public static String getCurrentLogFileName() {
        return FILE_PREFIX + currentFileNumber + FILE_EXTENSION;
    }
}