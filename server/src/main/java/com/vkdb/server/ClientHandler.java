package com.vkdb.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ClientHandler implements Runnable {
    private final SocketItem socketItem;
    private final LinkedBlockingQueue<NotifyItem> notificationsQueue;
    private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());
    private final ConcurrentHashMap<String, NotifyItem> keySocketsMap;
    private final LinkedBlockingQueue<SaveItem> diskWriteItems;
    private final ConcurrentHashMap<String, SaveItem> database;
    private final ConcurrentHashMap<String, AuthUser> authUsers;
    private final AtomicBoolean inTransaction = new AtomicBoolean(false);
    private final AtomicBoolean isLoggedIn = new AtomicBoolean(false);
    private final LinkedList<String> transactionList = new LinkedList<>();
    private String loggedInUsername;

    public ClientHandler(SocketItem socketItem, LinkedBlockingQueue<NotifyItem> notificationsQueue, ConcurrentHashMap<String, NotifyItem> keySocketsMap, LinkedBlockingQueue<SaveItem> diskWriteItems, ConcurrentHashMap<String, AuthUser> authUsers) {
        this.socketItem = socketItem;
        this.notificationsQueue = notificationsQueue;
        this.keySocketsMap = keySocketsMap;
        this.diskWriteItems = diskWriteItems;
        this.database = socketItem.getDatabase();
        this.authUsers = authUsers;
    }

    @Override
    public void run() {
        try {
            Socket socket = socketItem.getSocket();

            logger.info("Client connected from " + socketItem.getId());

            DataInputStream di = socketItem.getInputStream();
            DataOutputStream dou = socketItem.getOutputStream();

            while (true) {
                String originalCommand = di.readUTF();
                String[] commandParts = originalCommand.split(" ");
                String command = commandParts[0];
                String output = processCommand(originalCommand, command, commandParts);
                if (output == null) break;
                dou.writeUTF(output);
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
            logger.info("Got an error " + e.getLocalizedMessage());
        }
    }

    private String processCommand(String originalCommand, String command, String[] commandParts) {
        try {
            String output = "";

            // TODO : make sure i can disconnect without logging in
            if (!isLoggedIn.get() && !command.equals("LOGIN")) {
                output = "ERROR PLEASE LOGIN";
                return output;
            }

            switch (command) {
                case "DISCONNECT" -> output = "BYE";
                case "WHOAMI" -> output = loggedInUsername;
                case "REGISTER" -> {
                    if (commandParts.length != 3) {
                        output = "ERROR USAGE REGISTER <USERNAME> <PASSWORD>";
                    } else {
                        String username = commandParts[1];
                        String password = commandParts[2];
                        if (authUsers.containsKey(username)) {
                            output = "ERROR USERNAME EXISTS";
                        } else {
                            authUsers.put(username, new AuthUser(username, password));
                            output = "REGISTERED";
                        }
                    }
                }
                case "LOGIN" -> {
                    if (commandParts.length != 3) {
                        output = "ERROR USAGE LOGIN <USERNAME> <PASSWORD>";
                    } else {
                        String username = commandParts[1];
                        String password = commandParts[2];
                        if (authUsers.containsKey(username)) {
                            AuthUser authUser = authUsers.get(username);
                            if (!password.equals(authUser.getPassword())) {
                                output = "ERROR PASSWORD IS WRONG";
                            } else {
                                authUser.updateLastLoginTime();
                                isLoggedIn.set(true);
                                loggedInUsername = username;
                                output = "LOGIN SUCCESSFUL!";
                            }
                        } else {
                            output = "ERROR USER WITH USERNAME " + username + " NOT FOUND";
                        }
                    }
                }
                case "SET" -> {
                    if (commandParts.length != 3) {
                        output = "ERROR USAGE SET <KEY> <VALUE>";
                    } else {
                        if (inTransaction.get()) {
                            transactionList.addLast(originalCommand);
                            output = "SAVED TO BATCH";
                            break;
                        }
                        // we save it to a hashmap
                        String key = commandParts[1];
                        SaveItem saveItem = database.get(key);
                        String oldValue = saveItem == null ? "" : saveItem.getValue();
                        String newValue = commandParts[2];
                        if (!newValue.equals(oldValue)) {
                            // check if the key is marked as notified
                            if (keySocketsMap.containsKey(key)) {
                                NotifyItem item = keySocketsMap.get(key);       // Take the item from the key,socket map
                                item.setValue(newValue);                        // set the updated value
                                notificationsQueue.put(item);                   // put that item in a queue
                            }
                        }
                        saveItem = new SaveItem(key, newValue, "S");
                        diskWriteItems.put(saveItem); // adding it to the list
                        database.put(key, saveItem);
                        output = "SAVED";
                    }
                }
                case "SETX" -> {
                    if (commandParts.length != 4) {
                        output = "ERROR USAGE SETX <KEY> <VALUE> <TTL>";
                    } else {
                        if (inTransaction.get()) {
                            transactionList.addLast(originalCommand);
                            output = "SAVED TO BATCH";
                            break;
                        }
                        // we save it to a hashmap
                        String key = commandParts[1];
                        SaveItem saveItem = database.get(key);
                        String oldValue = saveItem == null ? "" : saveItem.getValue();
                        String newValue = commandParts[2];
                        long ttl;
                        try {
                            ttl = Long.parseLong(commandParts[3]);
                        } catch (NumberFormatException e) {
                            if (inTransaction.get()) output = null;
                            output = "ERROR <TTL> SHOULD BE NUMBER GOT : " + commandParts[2];
                            break;
                        }

                        if (!newValue.equals(oldValue)) {
                            // check if the key is marked as notified
                            if (keySocketsMap.containsKey(key)) {
                                NotifyItem item = keySocketsMap.get(key);
                                item.setValue(newValue);
                                notificationsQueue.put(item);
                            }
                        }
                        saveItem = new SaveItem(key, newValue, "SX", ttl);
                        diskWriteItems.put(saveItem); // adding it to the list
                        database.put(key, saveItem);
                        output = "SAVED";
                    }
                }
                case "GET" -> {
                    if (commandParts.length != 2) {
                        output = "ERROR USAGE GET <KEY>";
                    } else {
                        // we get it from hashmap if exists NOT FOUND if it doesn't
                        String key = commandParts[1];
                        SaveItem item = database.getOrDefault(key, null);
                        output = item == null ? "NOT FOUND" : item.getValue();
                    }
                }
                case "DEL" -> {
                    if (commandParts.length != 2) {
                        output = "ERROR USAGE DEL <KEY>";
                    } else {
                        if (inTransaction.get()) {
                            transactionList.addLast(originalCommand);
                            output = "SAVED TO BATCH";
                            break;
                        }
                        // we get it from hashmap if exists NOT FOUND if it doesn't
                        String key = commandParts[1];
                        if (database.containsKey(key)) {
                            SaveItem item = database.get(key);
                            item.setOperation("D");
                            database.remove(key);
                            diskWriteItems.put(item);
                            output = "DELETED";
                        } else {
                            output = "NOT FOUND";
                        }
                    }
                }
                case "START" -> {
                    // just a fake command for no use, but donot remove it in any case
                }
                case "USERLIST" -> {
                    for (String key : authUsers.keySet()) {
                        output = output.concat(key + "\n");
                    }
                }
                case "BEGIN" -> {
                    inTransaction.set(true);
                    transactionList.addLast("START");
                    output = "START";
                }
                case "COMMIT" -> {
                    if (transactionList.isEmpty()) {
                        output = "NO ITEMS FOUND";
                    } else {
                        inTransaction.set(false);
                        while (!transactionList.isEmpty()) {
                            String savedCommand = transactionList.poll();  // taking and removing the item from the linked-list
                            String[] parts = savedCommand.split(" ");
                            processCommand(savedCommand, parts[0], parts); // processing original command recursively
                        }
                        output = "COMMITTED";
                    }
                }
                case "NOTIFY" -> {
                    if (commandParts.length != 2) {
                        output = "ERROR USAGE NOTIFY <KEY>";
                    } else {
                        if (inTransaction.get()) {
                            transactionList.addLast(originalCommand);
                            output = "SAVED TO BATCH";
                            break;
                        }
                        // if any change in the given key, it gets notified by our server to the client
                        String key = commandParts[1];
                        // add it to a hashmap of to notify, it
                        NotifyItem item = keySocketsMap.getOrDefault(key, new NotifyItem(new HashSet<>()));
                        item.setKey(key);
                        item.getSocketItems().add(socketItem);
                        keySocketsMap.putIfAbsent(key, item);
                        output = "OK";
                    }
                }
                default -> output = "WRONG AVAILABLE ARE GET, SET, SETX, DEL, NOTIFY,BEGIN,COMMIT,LOGIN,REGISTER,WHOAMI";
            }

            return output;
        } catch (Exception e) {
            logger.info(e.getLocalizedMessage());
            return null;
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
