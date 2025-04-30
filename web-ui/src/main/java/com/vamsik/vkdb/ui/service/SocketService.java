package com.vamsik.vkdb.ui.service;

import org.springframework.stereotype.Service;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class SocketService {

    private static class SocketConnection {
        private final Socket socket;
        private final DataInputStream inputStream;
        private final DataOutputStream outputStream;

        public SocketConnection(String host, int port) throws IOException {
            this.socket = new Socket(host, port);
            this.inputStream = new DataInputStream(socket.getInputStream());
            this.outputStream = new DataOutputStream(socket.getOutputStream());
        }

        public void close() throws IOException {
            inputStream.close();
            outputStream.close();
            socket.close();
        }
    }

    public static class SocketEntry {
        private final String key;
        private final String value;
        private final long ttl;

        public SocketEntry(String key, String value, long ttl) {
            this.key = key;
            this.value = value;
            this.ttl = ttl;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        public long getTtl() {
            return ttl;
        }
    }

    public List<SocketEntry> getAllEntries(String host, int port) throws IOException {
        SocketConnection connection = new SocketConnection(host, port);
        try {
            connection.outputStream.writeUTF("LOGIN admin admin");
            connection.inputStream.readUTF();
            connection.outputStream.writeUTF("ALL");

            List<SocketEntry> entries = new ArrayList<>();
            String response = connection.inputStream.readUTF();
            if (!response.isEmpty()) {
                for (String entry : response.split("\n")) {
                    String[] parts = entry.split(" ");
                    entries.add(new SocketEntry(
                            parts[0],
                            parts[1],
                            Objects.equals(parts[2], "null") ? 0L : Long.parseLong(parts[2])
                    ));
                }
            }
            return entries;
        } finally {
            connection.close();
        }
    }

    public void setEntry(String host, int port, String key, String value, Long ttl) throws IOException {
        SocketConnection connection = new SocketConnection(host, port);
        try {
            connection.outputStream.writeUTF("LOGIN admin admin");
            connection.inputStream.readUTF();

            if (ttl != null) {
                connection.outputStream.writeUTF("SETX " + key + " " + value + " " + ttl);
            } else {
                connection.outputStream.writeUTF("SET " + key + " " + value);
            }
            connection.inputStream.readUTF(); // Read response
        } finally {
            connection.close();
        }
    }

    public void deleteEntry(String host, int port, String key) throws IOException {
        SocketConnection connection = new SocketConnection(host, port);
        try {
            connection.outputStream.writeUTF("LOGIN admin admin");
            connection.inputStream.readUTF();

            connection.outputStream.writeUTF("DEL " + key);
            connection.inputStream.readUTF(); // Read response
        } finally {
            connection.close();
        }
    }
} 