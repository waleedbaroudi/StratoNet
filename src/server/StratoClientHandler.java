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
    private final Socket clientSocket;
    private final StratoServer server;

    private final File usersFile;

    int passwordAttempts = 2;
    String inputUsername;
    String inputPassword;
    String correctPassword;

    long token;

    DataOutputStream writer;
    DataInputStream reader;

    public StratoClientHandler(Socket clientSocket, StratoServer server) {
        this.clientSocket = clientSocket;
        this.server = server;

        usersFile = new File(System.getProperty("user.dir") + "/users.txt");
        String info = String.format("Client info\nInet: %s\nport: %d\nlocal port: %d\nlocal address: %s\n"
                , clientSocket.getInetAddress().toString()
                , clientSocket.getPort()
                , clientSocket.getLocalPort()
                , clientSocket.getLocalAddress().toString());
        System.out.println(info);
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
            token = server.registerClient(inputUsername, clientSocket.getInetAddress().toString(), clientSocket.getPort());
            if (token == -1) { // Client with this username is already signed in
                sendMessage((byte) 0, (byte) 2, "User already signed in.");
                return false;
            }
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
}