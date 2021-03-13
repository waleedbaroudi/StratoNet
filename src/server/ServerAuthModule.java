package server;

import utils.StratoUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class ServerAuthModule {

    private final StratoClientHandler server;
    private final DataInputStream commandReader;
    private final DataOutputStream commandWriter;

    private final File usersFile;

    int passwordAttempts = 2;
    String inputUsername;
    String inputPassword;
    String correctPassword;

    private String token;


    public ServerAuthModule(StratoClientHandler server, DataInputStream commandReader, DataOutputStream commandWriter) {
        this.server = server;
        this.commandReader = commandReader;
        this.commandWriter = commandWriter;
        usersFile = new File(System.getProperty("user.dir") + "/users.txt");
    }

    boolean processAuthMessage() throws IOException {
        byte type = commandReader.readByte();
        String payload = new String(readMessage());

        if (type != 0) { // not Auth_Request
            System.out.println("[FATAL] UNKNOWN MESSAGE TYPE");
            return false;
        }

        System.out.println("[Request] " + payload);
        if (inputUsername == null) {
            inputUsername = payload;
            if (isValidUsername(inputUsername)) {
                sendMessage((byte) 1, "Password:");
                return true;
            }
            sendMessage((byte) 2, "Username does not exist.");
            return false;
        }

        inputPassword = payload;
        if (inputPassword.equals(correctPassword)) {
            token = server.registerClient(inputUsername);
            if (token == null) { // Client with this username is already signed in
                sendMessage((byte) 2, "User already signed in.");
                return false;
            }
            sendMessage((byte) 3, "Authenticated successfully!," + token); // send token
            sendMessage((byte) 6, "" + StratoServer.DATA_PORT); // send query connection info
            server.initializeQueryPhase();
            return true;
        }
        if (passwordAttempts > 0) {
            sendMessage((byte) 1, "Incorrect, remaining attempts: " + passwordAttempts + ". Password:");
            passwordAttempts--;
            return true;
        }
        sendMessage((byte) 2, "Incorrect password, out of attempts.");
        return false;
    }

    private byte[] readMessage() throws IOException {
        int length = commandReader.readInt();
        byte[] message = new byte[length];
        commandReader.readFully(message, 0, message.length);
        return message;
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

    private void sendMessage(byte type, String payload) throws IOException {
        byte[] message = StratoUtils.makeAuthMessage(type, payload);
        commandWriter.write(message);
    }

    public String getToken() {
        return token;
    }

    public void sendTimeOutMessage() throws IOException {
        sendMessage((byte) 2, "Connection timed out. Authentication failed.");
    }
}
