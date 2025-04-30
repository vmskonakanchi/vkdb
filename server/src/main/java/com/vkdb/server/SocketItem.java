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
    private final String username;
    private final String password;

    public SocketItem(String id, Socket socket, ConcurrentHashMap<String, SaveItem> database, DataOutputStream dataOutputStream, DataInputStream dataInputStream, String username, String password) {
        this.id = id;
        this.socket = socket;
        this.outputStream = dataOutputStream;
        this.inputStream = dataInputStream;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    public SocketItem(String id, Socket socket, ConcurrentHashMap<String, SaveItem> database, DataOutputStream dataOutputStream, DataInputStream dataInputStream) {
        this.id = id;
        this.socket = socket;
        this.outputStream = dataOutputStream;
        this.inputStream = dataInputStream;
        this.database = database;
        this.username = null;
        this.password = null;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
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
