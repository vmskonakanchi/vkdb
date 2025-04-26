package com.vkdb;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;
import java.util.logging.Logger;

public class Client {
    private static final Logger logger = Logger.getLogger(Client.class.getName());

    public static void main(String[] args) {
        try {
            String host = "localhost";
            int port = 6969;

            Socket socket = new Socket(host, port);
            logger.info("Connected to host : " + host + ":" + port);

            Scanner scanner = new Scanner(System.in);

            DataInputStream di = new DataInputStream(socket.getInputStream());
            DataOutputStream dou = new DataOutputStream(socket.getOutputStream());


            Thread readerThread = Thread.startVirtualThread(() -> {
                // reader thread
                label:
                while (true) {
                    try {
                        String response = di.readUTF();

                        // Clear the current line and print the response
                        System.out.print("\r                                         \r");  // Clear line
                        System.out.println(response);
                        // Reprint the prompt
                        System.out.print("vkdb> ");

                        switch (response) {
                            case "BYE": {
                                logger.info("Closing the connection");
                                break label;
                            }
                            default:
                                break;
                        }

                    } catch (EOFException | SocketException e) {
                        logger.warning("Server closed the connection");
                        break;
                    } catch (IOException e) {
                        logger.warning(e.getLocalizedMessage());
                    }
                }

                try {
                    socket.close();
                    di.close();
                    dou.close();
                    System.exit(0); // exiting the program
                } catch (Exception e) {
                    logger.warning(e.getLocalizedMessage());
                }
            });

            Thread writerThread = Thread.startVirtualThread(() -> {
                try {
                    String inputLine;

                    while (true) {
                        System.out.print("vkdb> ");
                        inputLine = scanner.nextLine();
                        if (inputLine != null && !inputLine.isEmpty()) {
                            dou.writeUTF(inputLine);
                            // If the command is to disconnect, break the loop
                            if (inputLine.equalsIgnoreCase("DISCONNECT")) {
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            writerThread.join();
            readerThread.join();
        } catch (SocketException |
                 EOFException e) {
            logger.info("Server not connected");
        } catch (
                Exception e) {
            e.printStackTrace();
        }
    }
}
