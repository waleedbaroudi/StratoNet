import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class StratoClientHandler extends Thread {
    private final Socket clientSocket;
    String username = "waroudi";
    String password = "6199";
    int passwordAttempts = 2;

    String inputUsername;
    String inputPassword;

    DataOutputStream writer;
    DataInputStream reader;

    public StratoClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public void run() {
        try {
            writer = new DataOutputStream(clientSocket.getOutputStream());
            reader = new DataInputStream(clientSocket.getInputStream());

            sendMessage((byte) 0, (byte) 5, "Welcome to StratoNet server");
            sendMessage((byte) 0, (byte) 1, "Username:");

            while (true) {
                if (!receiveMessage())
                    break;
            }
            writer.close();
            reader.close();
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private boolean receiveMessage() throws IOException {
        byte phase = reader.readByte();
        if (phase == 1) {
            System.out.println("WRONG PHASE");
            return false;
        }
        byte type = reader.readByte();
        int length = reader.readInt();
        byte[] message = new byte[length];
        reader.readFully(message, 0, message.length);
        return processMessage(message, type);
    }

    private boolean processMessage(byte[] message, byte type) throws IOException {
        String payload = new String(message);

        if (type != 0) { // Auth_Challenge
            System.out.println("[FATAL] UNKNOWN MESSAGE TYPE");
            return false;
        }

        System.out.println("[Request] " + payload);
        if (inputUsername == null) {
            inputUsername = payload;
            if (inputUsername.equals(username)) {
                sendMessage((byte) 0, (byte) 1, "Password:");
                return true;
            }
            sendMessage((byte) 0, (byte) 2, "Username does not exist.");
            return false;
        }

        inputPassword = payload;
        if (inputPassword.equals(password)) {
            sendMessage((byte) 0, (byte) 3, "Authenticated successfully!");
            return true;
        }
        if (passwordAttempts > 0) {
            sendMessage((byte) 0, (byte) 1, "Incorrect, remaining attempts: " + passwordAttempts + ". Password:");
            passwordAttempts--;
            return true;
        }
        sendMessage((byte) 0, (byte) 2, "Incorrect password, out of attempts.");
        return false;
    }

    private byte[] intToByte(int num) {
        return ByteBuffer.allocate(4).putInt(num).array();
    }

    private void sendMessage(byte phase, byte type, String payload) throws IOException {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        byte[] length = intToByte(payloadBytes.length);

        byte[] request = new byte[6 + payloadBytes.length];

        request[0] = phase;
        request[1] = type;

        System.arraycopy(length, 0, request, 2, 4);
        System.arraycopy(payloadBytes, 0, request, 6, payloadBytes.length);

        writer.write(request);
    }
}