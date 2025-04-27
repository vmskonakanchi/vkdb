package com.vkdb.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

public class Server {
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private static final ConcurrentHashMap<String, NotifyItem> keySocketsMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, SaveItem> database = new ConcurrentHashMap<>();
    private static final LinkedBlockingQueue<SaveItem> diskWriteItems = new LinkedBlockingQueue<>();
    public static final Path appendOnlyLogFilePath = Path.of("append-log.vdb");
    private static final long CACHE_CHECK_INTERVAL = 5 * 1000L; // cache check interval

    public static void main(String[] args) {
        try {
            int port = 6969;


            ServerSocket server = new ServerSocket(port);
            logger.info("Server is ready to accept connections on port " + port);

            try {
                Files.createFile(appendOnlyLogFilePath);   // creating file if not exists
            } catch (FileAlreadyExistsException e) {
                logger.info("Append only log file exists at " + appendOnlyLogFilePath.toAbsolutePath());
            }

            LinkedBlockingQueue<NotifyItem> notificationsQueue = new LinkedBlockingQueue<>();
            Thread.startVirtualThread(() -> handleNotifications(notificationsQueue)); // To Handle NOTIFY from other clients
            Thread.startVirtualThread(Server::handlePersistence); // To handle saving items
            Thread.startVirtualThread(Server::handleTtlExpiration);  // To handle expiring keys

            while (true) {
                Socket socket = server.accept(); // accepting new sockets
                String id = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
                SocketItem socketItem = new SocketItem(id, socket, database, new DataOutputStream(socket.getOutputStream()), new DataInputStream(socket.getInputStream()));
                Thread.startVirtualThread(new ClientHandler(socketItem, notificationsQueue, keySocketsMap, diskWriteItems)); // starting new thread
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
                Thread.sleep(CACHE_CHECK_INTERVAL); // sleeping becuase we don't want to check continuously

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

}
