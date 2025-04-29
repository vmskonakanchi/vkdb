package com.vkdb.server;

import org.apache.commons.cli.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

public class Server {
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private static final ConcurrentHashMap<String, NotifyItem> keySocketsMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, SaveItem> database = new ConcurrentHashMap<>();
    private static final LinkedBlockingQueue<SaveItem> diskWriteItems = new LinkedBlockingQueue<>();
    private static final ConcurrentHashMap<String, AuthUser> authUsers = new ConcurrentHashMap<>();
    private static final LinkedBlockingQueue<NotifyItem> notificationsQueue = new LinkedBlockingQueue<>();
    private static final LinkedList<SocketItem> replicas = new LinkedList<>();


    public static void main(String[] args) {
        int port = 6969;
        Options options = new Options();
        Option portOption = new Option("p", "port", true, "Port for the server to run");
        portOption.setRequired(false);

        options.addOption(portOption);
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
            port = Integer.parseInt(cmd.getOptionValue("port"));
        } catch (Exception e) {
            logger.info(e.getLocalizedMessage());
            formatter.printHelp("utility-name", options);
            System.exit(1);
            return;
        }

        try (ServerSocket server = new ServerSocket(port)) {
            logger.info("Server is ready to accept connections on port " + port);

            try {
                Files.createFile(Constants.APPEND_ONLY_LOG_FILE_PATH);   // creating file for append only log if not exists
            } catch (FileAlreadyExistsException e) {
                logger.info("Append only log file exists at " + Constants.APPEND_ONLY_LOG_FILE_PATH.toAbsolutePath());
            }

            try {
                Files.createFile(Constants.USER_LIST_PATH);   // creating file for storing users if not exists
            } catch (FileAlreadyExistsException e) {
                logger.info("Users list file exists at " + Constants.USER_LIST_PATH.toAbsolutePath());
            }

            // Starting 5 virtual threads to handle different tasks
            Thread.startVirtualThread(Server::handleNotifications);     // To handle notifications sending to other clients
            Thread.startVirtualThread(Server::handlePersistence);       // To handle saving items
            Thread.startVirtualThread(Server::handleTtlExpiration);     // To handle expiring keys
            Thread.startVirtualThread(Server::handleCompaction);        // to handle compaction
            Thread.startVirtualThread(Server::handleLoadUsers);

            // Shutdown hook , called before shutting down jvm
            Runtime.getRuntime().addShutdownHook(new Thread(Server::handleShutDown));

            while (true) {
                Socket socket = server.accept(); // accepting new sockets
                String id = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
                SocketItem socketItem = new SocketItem(id, socket, database, new DataOutputStream(socket.getOutputStream()), new DataInputStream(socket.getInputStream()));
                Thread.startVirtualThread(new ClientHandler(socketItem, notificationsQueue, keySocketsMap, diskWriteItems, authUsers, replicas)); // starting new thread
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleNotifications() {
        try {
            while (true) {
                // we take an item from queue
                NotifyItem notifyItem = notificationsQueue.take();
                logger.info("Got a notification from to process for a key" + notifyItem.getKey());

                for (SocketItem socketItem : notifyItem.getSocketItems()) {
                    logger.info("Got a key : " + notifyItem.getKey() + " to notify to " + socketItem.getId());

                    DataOutputStream dis = socketItem.getOutputStream();
                    dis.writeUTF("CHANGED " + notifyItem.getKey() + " " + notifyItem.getValue());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handlePersistence() {
        try (BufferedReader reader = new BufferedReader(new FileReader(Constants.APPEND_ONLY_LOG_FILE_PATH.toFile()))) {

            // replaying the append only log file format for setting the data
            logger.info("Reading the append only log file at : " + Constants.APPEND_ONLY_LOG_FILE_PATH.toAbsolutePath());

            reader.lines().forEach(line -> {
                String[] parts = line.split("=");
                if (parts.length == 3) {
                    // correct entry add it to the database
                    String operation = parts[0];
                    String key = parts[1];
                    String value = parts[2];

                    switch (operation) {
                        case "S", "SX" -> database.put(key, new SaveItem(key, value, "S"));
                        case "D" -> database.remove(key);
                        default -> logger.info("No operation");
                    }
                } else {
                    // wrong entry ignore it and log it
                    logger.info("Malformed entry in the file : " + line);
                }

            });

            logger.info("Reading completed saved : " + database.size() + " entries");


            while (true) {
                SaveItem item = diskWriteItems.take();
                logger.info("Got a item to save with key : " + item.getKey() + " with value " + item.getValue());

                try (FileOutputStream fos = new FileOutputStream(Constants.APPEND_ONLY_LOG_FILE_PATH.toFile(), true)) {
                    fos.write(item.toString().getBytes());
                    fos.flush();
                    fos.getFD().sync(); // Ensures data is on disk
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void handleTtlExpiration() {
        try {
            while (true) {
                Thread.sleep(Constants.CACHE_CHECK_INTERVAL); // sleeping because we don't want to check continuously

                // checking continuously for ttl
                logger.info("Checking ttl");
                for (SaveItem item : database.values()) {
                    if (item.hasExpired()) {  // if the item has expired , remove it
                        logger.info("key : " + item.getKey() + " expired , removing it");
                        database.remove(item.getKey());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleCompaction() {
        try {
            while (true) {
                Thread.sleep(Constants.COMPACTION_INTERVAL);

                logger.info("Starting compaction");

                Map<String, SaveItem> latestEntries = new LinkedHashMap<>();
                Set<String> deletedKeys = new HashSet<>();

                try (RandomAccessFile accessFile = new RandomAccessFile(Constants.APPEND_ONLY_LOG_FILE_PATH.toFile(), "r")) {
                    if (accessFile.length() == 0) continue; // skipping if we have 0 entries
                    long pointer = accessFile.length() - 1;
                    StringBuilder lineBuilder = new StringBuilder();

                    while (pointer >= 0) {
                        accessFile.seek(pointer);
                        int readByte = accessFile.readByte();

                        if (readByte == '\n') {
                            if (!lineBuilder.isEmpty()) {
                                processLine(lineBuilder.reverse().toString(), latestEntries, deletedKeys);
                                lineBuilder.setLength(0);
                            }
                        } else {
                            lineBuilder.append((char) readByte);
                        }
                        pointer--;
                    }

                    if (!lineBuilder.isEmpty()) {
                        processLine(lineBuilder.reverse().toString(), latestEntries, deletedKeys);
                    }
                } catch (IOException e) {
                    logger.info(e.getLocalizedMessage());
                }

                // Write the compacted result
                writeCompactedFile(latestEntries);
                logger.info("Compaction completed for the file");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleLoadUsers() {
        logger.info("Started loading users");
        try (BufferedReader reader = new BufferedReader(new FileReader(Constants.USER_LIST_PATH.toFile()))) {

            reader.lines().forEach(line -> {
                String[] parts = line.split("-");

                if (parts.length == 2) {
                    String username = parts[0];
                    String password = parts[1];
                    authUsers.putIfAbsent(username, new AuthUser(username, password));
                } else {
                    logger.info("Malformed entry when loading users at line : " + line);
                }
            });


            logger.info("Users loading completed found " + authUsers.size() + " users");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleShutDown() {
        logger.info("Shutdown initiated. Flushing disk write queue...");
        try {
            SaveItem item;
            while ((item = diskWriteItems.poll()) != null) {
                try (FileOutputStream fos = new FileOutputStream(Constants.APPEND_ONLY_LOG_FILE_PATH.toFile(), true)) {
                    fos.write(item.toString().getBytes());
                    fos.flush();
                    fos.getFD().sync();
                }
            }
            logger.info("All pending disk writes flushed.");
        } catch (Exception e) {
            logger.severe("Error during shutdown flush: " + e.getMessage());
        }
        logger.info("vkdb server shutdown complete.");
    }


    private static void processLine(String line, Map<String, SaveItem> latestEntries, Set<String> deletedKeys) {
        line = line.trim();

        if (line.isEmpty()) return;

        String[] parts = line.split("=");

        if (parts.length == 3) {
            String operation = parts[0];
            String key = parts[1];
            String value = parts[2];
            switch (operation) {
                case "S", "SX" -> {
                    if (!latestEntries.containsKey(key) && !deletedKeys.contains(key)) {
                        latestEntries.put(key, new SaveItem(key, value, operation));
                    }
                }
                case "D" -> deletedKeys.add(key);
            }
        } else {
            logger.info("Malformed entry skipping compaction for this line : " + line);
        }
    }


    private static void writeCompactedFile(Map<String, SaveItem> latestEntries) {
        try {
            Path compactedFilePath = Paths.get("compacted_file_" + System.currentTimeMillis() + ".vdb"); // or overwrite original

            try (BufferedWriter writer = Files.newBufferedWriter(compactedFilePath)) {
                for (Map.Entry<String, SaveItem> entry : latestEntries.entrySet()) {
                    writer.write(entry.getValue().toString());
                }
            }

            // Atomically replace the original file with the compacted one
            Files.move(compactedFilePath, Constants.APPEND_ONLY_LOG_FILE_PATH, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
