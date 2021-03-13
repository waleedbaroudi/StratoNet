package server;

import utils.StratoUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
    private int currentPhase = 0;

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
            commandSocket.setSoTimeout(10000);

            sendMessage((byte) 0, (byte) 5, "Welcome to StratoNet server");
            sendMessage((byte) 0, (byte) 1, "Username:");

            while (true) {
                if (!receiveMessage())
                    break;
            }

            closeConnection();
        } catch (SocketTimeoutException e) {
            sendTimeOutMessage();
        } catch (SocketException e) {
            System.err.println("Client disconnected");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            server.unregisterClient(token);
        }
    }

    private void closeConnection() throws IOException {

        commandWriter.close();
        reader.close();
        commandSocket.close();
        if (dataWriter != null) {
            dataWriter.close();
            dataSocket.close();
        }
    }

    private boolean receiveMessage() throws IOException {
        byte phase = reader.readByte();
        if (phase != currentPhase) {
            rejectMessage();
            return false;
        }
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
            sendMessage((byte) 1, (byte) 4, "Access Denied: invalid token");
            return false;
        }

        switch (type) {
            case 1:
                handleApodRequest(new String(message));
                return true;
            case 2:
                handleInsightRequest(new String(message));
                return true;
            case 5:
                System.out.println("Acknowledged: " + new String(message));
                // set timeout back to 10 seconds
                commandSocket.setSoTimeout(10000);
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


    private void sendTimeOutMessage() {
        System.out.println("Client " + commandSocket.getPort() + " timed out.");
        try {
            if (currentPhase == 0)
                sendMessage((byte) 0, (byte) 2, "Connection timed out. Authentication failed.");
            else
                sendMessage((byte) 1, (byte) 4, "Connection timed out, terminating session..");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendData(byte type, int length, byte[] data) throws IOException {
        dataWriter.write(type);
        dataWriter.write(StratoUtils.intToByte(length));
        dataWriter.write(data);
    }

    private void initializeQueryPhase() throws IOException {
        currentPhase = 1;
        sendMessage((byte) 0, (byte) 6, "" + StratoServer.DATA_PORT); // send auth_connect with the query connection info
        dataSocket = server.getDataSocket();
        dataWriter = new DataOutputStream(dataSocket.getOutputStream());
        System.out.println("user connected to data socket");
    }

    private void handleApodRequest(String param) throws IOException {
        startProcessingState();
        URL url = new URL(StratoUtils.APOD_URL + param);
        String response = server.apiRequest(url);
        if (response == null) {
            sendMessage((byte) 1, (byte) 4, "Invalid request: no results found.");
            return;
        }
        String imageUrl = StratoUtils.extractURL(response);
        if (imageUrl == null) { // no image url in the returned json object
            sendMessage((byte) 1, (byte) 4, "No image found with the given date.");
            return;
        }
        // image url found
        byte[] image = StratoUtils.downloadImage(imageUrl);
        sendMessage((byte) 1, (byte) 0, StratoUtils.generateHash(1, image));
        sendData((byte) 1, image.length, image);
    }

    private void handleInsightRequest(String param) throws IOException {
        startProcessingState();
        URL url = new URL(StratoUtils.INSIGHT_URL);
        String response = server.apiRequest(url);
        if (response == null) {
            sendMessage((byte) 1, (byte) 4, "Invalid request: no results found.");
            return;
        }
        String[] solPREList = StratoUtils.extractPREObjects(response);
        byte[] data = solPREList[Integer.parseInt(param) - 1].getBytes(StandardCharsets.UTF_8);
        sendMessage((byte) 1, (byte) 0, StratoUtils.generateHash(2, data));
        sendData((byte) 2, data.length, data);
    }

    private void startProcessingState() throws IOException {
        sendMessage((byte) 1, (byte) 3, "Processing request..");
        commandSocket.setSoTimeout(0);
    }

    private void rejectMessage() throws IOException {
        if (currentPhase == 0)
            sendMessage((byte) currentPhase, (byte) 2, "Authentication phase already passed.");
        else
            sendMessage((byte) currentPhase, (byte) 4, "Access Denied: unauthorized user");
    }
}