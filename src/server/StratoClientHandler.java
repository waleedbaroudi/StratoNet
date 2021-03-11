package server;

import utils.StratoUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
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

    DataOutputStream writer;
    DataInputStream reader;

    public StratoClientHandler(Socket commandSocket, StratoServer server) {
        this.commandSocket = commandSocket;
        this.server = server;

        usersFile = new File(System.getProperty("user.dir") + "/users.txt");
    }

    public void run() {
        try {
            writer = new DataOutputStream(commandSocket.getOutputStream());
            reader = new DataInputStream(commandSocket.getInputStream());

            sendMessage((byte) 0, (byte) 5, "Welcome to StratoNet server");
            sendMessage((byte) 0, (byte) 1, "Username:");

            while (true) {
                if (!receiveMessage())
                    break;
            }
            writer.close();
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
            writer.write(StratoUtils.makeAuthMessage(type, payload));
        else { // query

        }
    }

    private void initializeQueryPhase() throws IOException {
        sendMessage((byte) 0, (byte) 6, "" + StratoServer.DATA_PORT); // send auth_connect with the query connection info
        dataSocket = server.getDataSocket();
        System.out.println("user connected to data socket");
    }
}