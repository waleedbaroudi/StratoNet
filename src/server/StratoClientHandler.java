package server;

import utils.StratoUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.Scanner;

public class StratoClientHandler extends Thread {
    private final Socket commandSocket;
    private Socket dataSocket;
    private final StratoServer server;

    private final File usersFile;

    int passwordAttempts = 2;
    String inputUsername;
    String inputPassword;
    String correctPassword;

    String token;

    DataOutputStream commandWriter;
    DataOutputStream dataWriter;
    DataInputStream reader;

    public StratoClientHandler(Socket commandSocket, StratoServer server) {
        this.commandSocket = commandSocket;
        this.server = server;

        usersFile = new File(System.getProperty("user.dir") + "/users.txt");
    }

    public void run() {
        try {
            commandWriter = new DataOutputStream(commandSocket.getOutputStream());
            reader = new DataInputStream(commandSocket.getInputStream());

            sendMessage((byte) 0, (byte) 5, "Welcome to StratoNet server");
            sendMessage((byte) 0, (byte) 1, "Username:");

            while (true) {
                if (!receiveMessage())
                    break;
            }
            commandWriter.close();
            reader.close();
            commandSocket.close();
        } catch (SocketException e) {
            System.err.println("Client disconnected");
            server.unregisterClient(token);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private boolean receiveMessage() throws IOException {
        byte phase = reader.readByte();
        if (phase == 0) { // auth
            byte type = reader.readByte();
            int length = reader.readInt();
            byte[] message = new byte[length];
            reader.readFully(message, 0, message.length);
            return processAuthMessage(message, type);
        }
        if (phase == 1) { // query
            byte[] receivedToken = new byte[StratoUtils.TOKEN_LENGTH];
            reader.readFully(receivedToken, 0, receivedToken.length);
            byte type = reader.readByte();
            int length = reader.readInt();
            byte[] message = new byte[length];
            reader.readFully(message, 0, message.length);
            return processQueryMessage(receivedToken, message, type);
        }
        return false;
    }

    private boolean processAuthMessage(byte[] message, byte type) throws IOException {
        String payload = new String(message);

        if (type != 0) { // not Auth_Request
            System.out.println("[FATAL] UNKNOWN MESSAGE TYPE");
            return false;
        }

        System.out.println("[Request] " + payload);
        if (inputUsername == null) {
            inputUsername = payload;
            if (isValidUsername(inputUsername)) {
                sendMessage((byte) 0, (byte) 1, "Password:");
                return true;
            }
            sendMessage((byte) 0, (byte) 2, "Username does not exist.");
            return false;
        }

        inputPassword = payload;
        if (inputPassword.equals(correctPassword)) {
            token = server.registerClient(inputUsername, commandSocket.getInetAddress().toString(), commandSocket.getPort());
            if (token == null) { // Client with this username is already signed in
                sendMessage((byte) 0, (byte) 2, "User already signed in.");
                return false;
            }
            sendMessage((byte) 0, (byte) 3, "Authenticated successfully!," + token);
            initializeQueryPhase();
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

    private boolean processQueryMessage(byte[] receivedToken, byte[] message, byte type) throws IOException {
        if (!server.isRegisteredToken(new String(receivedToken), commandSocket.getInetAddress().toString(), commandSocket.getPort())) {
            sendMessage((byte) 1, (byte) 4, "Access Denied: Unauthorized user");
            return false;
        }

        switch (type) {
            case 1:
                sendMessage((byte) 1, (byte) 3, "Processing request..");
                handleApodRequest(new String(message));
                return true;
            case 2:
                handleInsightRequest(new String(message));
                return true;
            default:
                sendMessage((byte) 1, (byte) 4, "Unknown API Operation");
                return false;
        }
    }

    private boolean isValidUsername(String name) throws IOException {
        Scanner fileScanner = new Scanner(usersFile);
        String username;
        while (fileScanner.hasNextLine()) {
            username = fileScanner.nextLine();
            if (name.equals(username)) {
                correctPassword = fileScanner.nextLine();
                return true;
            }
            fileScanner.nextLine(); // skip password line
            fileScanner.nextLine(); // skip empty line
        }
        return false;
    }

    private void sendMessage(byte phase, byte type, String payload) throws IOException {
        if (phase == 0) // auth
            commandWriter.write(StratoUtils.makeAuthMessage(type, payload));
        else  // query
            commandWriter.write(StratoUtils.makeQueryMessage(token, type, payload));
    }

    private void sendData(int length, byte[] data) throws IOException {
        dataWriter.write(StratoUtils.intToByte(length));
        dataWriter.write(data);
    }

    private void initializeQueryPhase() throws IOException {
        sendMessage((byte) 0, (byte) 6, "" + StratoServer.DATA_PORT); // send auth_connect with the query connection info
        dataSocket = server.getDataSocket();
        dataWriter = new DataOutputStream(dataSocket.getOutputStream());
        System.out.println("user connected to data socket");
    }

    private void handleApodRequest(String param) throws IOException {
        URL url = new URL(StratoUtils.APOD_URL + "&" + param);
        byte[] response = server.requestApod(url);
        sendMessage((byte) 1, (byte) 0, "data retrieved"); // todo: change to send hash instead
        sendData(response.length, response);
    }

    private void handleInsightRequest(String param) {

    }
}