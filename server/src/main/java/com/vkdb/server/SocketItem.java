package com.vkdb.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class SocketItem {
    private final Socket socket;
    private final DataOutputStream outputStream;
    private final DataInputStream inputStream;
    private final ConcurrentHashMap<String, SaveItem> database;
    private final String id;

    public SocketItem(String id, Socket socket, ConcurrentHashMap<String, SaveItem> database, DataOutputStream dataOutputStream, DataInputStream dataInputStream) {
        this.id = id;
        this.socket = socket;
        this.outputStream = dataOutputStream;
        this.inputStream = dataInputStream;
        this.database = database;
    }

    public String getId() {
        return this.id;
    }

    public Socket getSocket() {
        return this.socket;
    }

    public ConcurrentHashMap<String, SaveItem> getDatabase() {
        return this.database;
    }

    public DataOutputStream getOutputStream() {
        return outputStream;
    }

    public DataInputStream getInputStream() {
        return inputStream;
    }
}
