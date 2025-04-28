import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

public class ServerLoadTester {
    private static final int MAX_CLIENTS = 100;
    private static final int MAX_HITS = 10;

    public static void main(String[] args) {
        try {
            List<Thread> writeClients = new ArrayList<>();
            List<Thread> readClients = new ArrayList<>();

            for (int i = 0; i < MAX_CLIENTS; i++) {
                Socket socket = new Socket("localhost", 6969);
                Thread writeThread = Thread.startVirtualThread(() -> connectAndTestClient(socket, false));
                Thread readThread = Thread.startVirtualThread(() -> connectAndTestClient(socket, true));

                writeClients.add(writeThread);
                readClients.add(readThread);
            }

            for (Thread t : writeClients) {
                t.join();
            }

            for (Thread t : readClients) {
                t.join();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void connectAndTestClient(Socket socket, boolean isRead) {
        try (DataOutputStream dou = new DataOutputStream(socket.getOutputStream());
             DataInputStream di = new DataInputStream(socket.getInputStream());
        ) {
            int hitCount = 0;
            while (hitCount != MAX_HITS) {
                String millSeconds = String.valueOf(System.currentTimeMillis());

                String key = "key_" + millSeconds;
                String value = "value_" + millSeconds;

                if (isRead) {
                    dou.writeUTF("GET " + key);
                } else {
                    dou.writeUTF("SET " + key + " " + value);
                }
                System.out.println("Server : " + di.readUTF());
                hitCount++;
            }
            socket.close();
        } catch (EOFException | SocketException e) {
            System.out.println("Client Disconnected");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
