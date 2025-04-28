package com.vkdb.server;

import javax.swing.text.DefaultEditorKit;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

public class Server {
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private static final ConcurrentHashMap<String, NotifyItem> keySocketsMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, SaveItem> database = new ConcurrentHashMap<>();
    private static final LinkedBlockingQueue<SaveItem> diskWriteItems = new LinkedBlockingQueue<>();
    private static final ConcurrentHashMap<String, AuthUser> authUsers = new ConcurrentHashMap<>();
    private static final Path appendOnlyLogFilePath = Path.of("append-log.vdb");
    private static final Path usersListPath = Path.of("users.vdb");
    private static final long CACHE_CHECK_INTERVAL = 10 * 1000L; // cache check interval
    private static final long COMPACTION_INTERVAL = 200 * 1000L;  // compaction interval


    public static void main(String[] args) {
        int port = 6969;
        try (ServerSocket server = new ServerSocket(port)) {
            logger.info("Server is ready to accept connections on port " + port);

            try {
                Files.createFile(appendOnlyLogFilePath);   // creating file for append only log if not exists
            } catch (FileAlreadyExistsException e) {
                logger.info("Append only log file exists at " + appendOnlyLogFilePath.toAbsolutePath());
            }

            try {
                Files.createFile(usersListPath);   // creating file for storing users if not exists
            } catch (FileAlreadyExistsException e) {
                logger.info("Users list file exists at " + usersListPath.toAbsolutePath());
            }

            LinkedBlockingQueue<NotifyItem> notificationsQueue = new LinkedBlockingQueue<>();

            Thread.startVirtualThread(() -> handleNotifications(notificationsQueue)); // To Handle NOTIFY from other clients
            Thread.startVirtualThread(Server::handlePersistence); // To handle saving items
            Thread.startVirtualThread(Server::handleTtlExpiration);  // To handle expiring keys
            Thread.startVirtualThread(Server::doCompaction);  // to handle compaction

            // adding a default user
            authUsers.putIfAbsent("admin", new AuthUser("admin", "admin"));

            while (true) {
                Socket socket = server.accept(); // accepting new sockets
                String id = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
                SocketItem socketItem = new SocketItem(id, socket, database, new DataOutputStream(socket.getOutputStream()), new DataInputStream(socket.getInputStream()));
                Thread.startVirtualThread(new ClientHandler(socketItem, notificationsQueue, keySocketsMap, diskWriteItems, authUsers)); // starting new thread
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleNotifications(LinkedBlockingQueue<NotifyItem> notifyItems) {
        try {
            while (true) {
                // we take an item from queue
                NotifyItem notifyItem = notifyItems.take();
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
        try {
            // replaying the append only log file format for setting the data
            BufferedReader reader = new BufferedReader(new FileReader(appendOnlyLogFilePath.toFile()));

            logger.info("Reading the append only log file at : " + appendOnlyLogFilePath.toAbsolutePath());

            reader.lines().forEach(line -> {
                String[] parts = line.split("=");
                if (parts.length == 3) {
                    // correct entry add it to the database
                    String operation = parts[0];
                    String key = parts[1];
                    String value = parts[2];

                    switch (operation) {
                        case "S" -> database.put(key, new SaveItem(key, value, "S"));
                        case "D" -> database.remove(key);
                        default -> logger.info("No operation");
                    }
                } else {
                    // wrong entry ignore it and log it
                    logger.info("Malformed entry in the file : " + line);
                }

            });

            reader.close();

            logger.info("Reading completed saved : " + database.size() + " entries");


            while (true) {
                SaveItem item = diskWriteItems.take();
                logger.info("Got a item to save with key : " + item.getKey() + " with value " + item.getValue());

                Files.writeString(appendOnlyLogFilePath, item.toString(), StandardOpenOption.APPEND);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void handleTtlExpiration() {
        try {
            while (true) {
                Thread.sleep(CACHE_CHECK_INTERVAL); // sleeping because we don't want to check continuously

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

    private static void doCompaction() {
        try {
            while (true) {
                Thread.sleep(COMPACTION_INTERVAL);

                logger.info("Starting compaction");

                Map<String, SaveItem> latestEntries = new LinkedHashMap<>();
                Set<String> deletedKeys = new HashSet<>();

                try (RandomAccessFile accessFile = new RandomAccessFile(appendOnlyLogFilePath.toFile(), "r")) {
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
            Files.move(compactedFilePath, appendOnlyLogFilePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
