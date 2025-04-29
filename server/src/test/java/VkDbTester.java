import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VkDbTester {
    private Socket socket;
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;

    @BeforeAll
    public void setUpSocket() throws Exception {
        socket = new Socket("localhost", 6969);
        dataOutputStream = new DataOutputStream(socket.getOutputStream());
        dataInputStream = new DataInputStream(socket.getInputStream());
    }

    @AfterAll
    public void closeSocket() throws Exception {
        if (socket != null && !socket.isClosed()) {
            dataOutputStream.close();
            dataInputStream.close();
            socket.close();
        }
    }

    @AfterEach
    public void flushData() throws Exception {
        dataOutputStream.flush();
    }

    @Test
    @Order(0)
    public void checkConnection() {
        assertTrue(socket.isConnected());
    }

    @Test
    @Order(1)
    public void checkCorrectLogin() throws Exception {
        dataOutputStream.writeUTF("LOGIN admin admin");
        assertEquals("LOGIN SUCCESSFUL!", dataInputStream.readUTF());
    }

    @Test
    @Order(2)
    public void setKeyValue() throws Exception {
        dataOutputStream.writeUTF("SET name vamsi");
        assertEquals("SAVED", dataInputStream.readUTF());
    }

    @Test
    @Order(3)
    public void getValue() throws Exception {
        dataOutputStream.writeUTF("GET name");
        assertEquals("vamsi", dataInputStream.readUTF());
    }

    @Test
    @Order(4)
    public void checkWhoAmi() throws Exception {
        dataOutputStream.writeUTF("WHOAMI");
        assertEquals("admin", dataInputStream.readUTF());
    }

    @Test
    @Order(5)
    public void deleteValue() throws Exception {
        dataOutputStream.writeUTF("DEL name");
        assertEquals("DELETED", dataInputStream.readUTF());
    }

    @Test
    @Order(6)
    public void checkDeletedValue() throws Exception {
        dataOutputStream.writeUTF("GET name");
        assertEquals("NOT FOUND", dataInputStream.readUTF());
    }

    @Test
    @Order(7)
    public void setKeyValueWithExpiration() throws Exception {
        dataOutputStream.writeUTF("SETX username pavan 3600");
        assertEquals("SAVED", dataInputStream.readUTF());
    }

    @Test
    @Order(8)
    public void checkKeyValueWithExpiration() throws Exception {
        dataOutputStream.writeUTF("GET username");
        assertEquals("pavan", dataInputStream.readUTF());
    }

    @Test
    @Order(9)
    public void setTransaction() throws Exception {
        dataOutputStream.writeUTF("BEGIN");
        assertEquals("START", dataInputStream.readUTF());
        dataOutputStream.writeUTF("SET count 1");
        assertEquals("SAVED TO BATCH", dataInputStream.readUTF());
        dataOutputStream.writeUTF("SET count 2");
        assertEquals("SAVED TO BATCH", dataInputStream.readUTF());
        dataOutputStream.writeUTF("SET count 3");
        assertEquals("SAVED TO BATCH", dataInputStream.readUTF());
        dataOutputStream.writeUTF("SET visitors 1024");
        assertEquals("SAVED TO BATCH", dataInputStream.readUTF());
        dataOutputStream.writeUTF("DEL count");
        assertEquals("SAVED TO BATCH", dataInputStream.readUTF());
        dataOutputStream.writeUTF("GET count");
        assertEquals("NOT FOUND", dataInputStream.readUTF());
        dataOutputStream.writeUTF("COMMIT");
        assertEquals("COMMITTED", dataInputStream.readUTF());
        dataOutputStream.writeUTF("GET visitors");
        assertEquals("1024", dataInputStream.readUTF());
        dataOutputStream.writeUTF("GET count");
        assertEquals("NOT FOUND", dataInputStream.readUTF());
    }

    @Test
    @Order(10)
    public void getKeyValueWithExpiration() throws Exception {
        Thread.sleep(11000L);
        dataOutputStream.writeUTF("GET username");
        assertEquals("NOT FOUND", dataInputStream.readUTF());
    }

    @Test
    @Order(11)
    public void registerNewUser() throws Exception {
        dataOutputStream.writeUTF("REGISTER vamsi 123");
        assertEquals("REGISTERED", dataInputStream.readUTF());
    }

    @Test
    @Order(12)
    public void checkRegister() throws Exception {
        dataOutputStream.writeUTF("LOGIN vamsi 123");
        assertEquals("LOGIN SUCCESSFUL!", dataInputStream.readUTF());
        dataOutputStream.writeUTF("WHOAMI");
        assertEquals("vamsi", dataInputStream.readUTF());
    }
}