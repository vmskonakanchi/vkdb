package com.vkdb.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

public class Server {
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private static final ConcurrentHashMap<String, NotifyItem> keySocketsMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> database = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try {
            int port = 6969;


            ServerSocket server = new ServerSocket(port);
            logger.info("Server is ready to accept connections on port " + port);

            LinkedBlockingQueue<NotifyItem> notificationsQueue = new LinkedBlockingQueue<>();
            Thread.startVirtualThread(() -> handleNotifications(notificationsQueue)); // To Handle NOTIFY from other clients

            while (true) {
                Socket socket = server.accept(); // accepting new sockets
                String id = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
                SocketItem socketItem = new SocketItem(id, socket, database, new DataOutputStream(socket.getOutputStream()), new DataInputStream(socket.getInputStream()));
                Thread.startVirtualThread(new ClientHandler(socketItem, notificationsQueue, keySocketsMap)); // starting new thread
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

    private void handlePersistence() {

    }

}
