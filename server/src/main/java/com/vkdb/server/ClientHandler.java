package com.vkdb.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {
    private final SocketItem socketItem;
    private final LinkedBlockingQueue<NotifyItem> notificationsQueue;
    private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());
    private final ConcurrentHashMap<String, NotifyItem> keySocketsMap;

    public ClientHandler(SocketItem socketItem, LinkedBlockingQueue<NotifyItem> notificationsQueue, ConcurrentHashMap<String, NotifyItem> keySocketsMap) {
        this.socketItem = socketItem;
        this.notificationsQueue = notificationsQueue;
        this.keySocketsMap = keySocketsMap;
    }

    @Override
    public void run() {
        try {
            Socket socket = socketItem.getSocket();

            ConcurrentHashMap<String, String> database = socketItem.getDatabase();

            logger.info("Client connected from " + socketItem.getId());

            DataInputStream di = socketItem.getInputStream();
            DataOutputStream dou = socketItem.getOutputStream();

            label:
            while (true) {
                String[] commandParts = di.readUTF().split(" ");
                String command = commandParts[0];

                switch (command) {
                    case "DISCONNECT": {
                        dou.writeUTF("BYE");
                        break label;
                    }
                    case "SET": {
                        if (commandParts.length != 3) {
                            dou.writeUTF("ERROR USAGE SET <KEY> <VALUE>");
                        } else {
                            // we save it to a hashmap for now
                            String key = commandParts[1];
                            String oldValue = database.get(key);
                            String newValue = commandParts[2];
                            if (!newValue.equals(oldValue)) {
                                // TODO : Notify the client who has been asked to notify
                                // check if the key is marked as notified
                                if (keySocketsMap.containsKey(key)) {
                                    NotifyItem item = keySocketsMap.get(key);
                                    item.setValue(newValue);
                                    notificationsQueue.put(item);
                                }
                            }
                            database.put(commandParts[1], newValue);
                            dou.writeUTF("SAVED");
                        }
                        break;
                    }
                    case "GET": {
                        if (commandParts.length != 2) {
                            dou.writeUTF("ERROR USAGE GET <KEY>");
                        } else {
                            // we get it from hashmap if exists NOT FOUND if it doesn't
                            String key = commandParts[1];
                            dou.writeUTF(database.getOrDefault(key, "NOT FOUND"));
                        }
                        break;
                    }
                    case "DEL": {
                        if (commandParts.length != 2) {
                            dou.writeUTF("ERROR USAGE DEL <KEY>");
                        } else {
                            // we get it from hashmap if exists NOT FOUND if it doesn't
                            String key = commandParts[1];
                            if (database.containsKey(key)) {
                                database.remove(key);
                                dou.writeUTF("DELETED");
                            } else {
                                dou.writeUTF("NOT FOUND");
                            }
                        }
                        break;
                    }
                    case "NOTIFY": {
                        if (commandParts.length != 2) {
                            dou.writeUTF("ERROR USAGE NOTIFY <KEY>");
                        } else {
                            // if any change in the given key, it gets notified by our server to the client
                            String key = commandParts[1];
                            // add it to a hashmap of to notify, it
                            NotifyItem item = keySocketsMap.getOrDefault(key, new NotifyItem(new HashSet<>()));
                            item.setKey(key);
                            item.getSocketItems().add(socketItem);
                            keySocketsMap.putIfAbsent(key, item);
                            dou.writeUTF("OK");
                        }
                        break;
                    }
                    default:
                        dou.writeUTF("WRONG AVAILABLE ARE GET SET DEL NOTIFY");
                        break;
                }
            }
            cleanupNotifications();
            if (socket.isClosed()) {
                logger.info("Client Disconnected from " + socketItem.getId());
            } else {
                socket.close();
            }
        } catch (EOFException | SocketException e) {
            cleanupNotifications();
            logger.info("Client Disconnected from " + socketItem.getId());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cleanupNotifications() {
        // Iterate through all notification entries
        for (NotifyItem item : keySocketsMap.values()) {
            // Remove this client's socketItem from the notification list
            item.getSocketItems().removeIf(socket -> socket.getId().equals(socketItem.getId()));

            // If no clients are left for this notification, remove the entire entry
            if (item.getSocketItems().isEmpty()) {
                keySocketsMap.remove(item.getKey());
            }
        }
    }
}
