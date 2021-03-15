package server;

import utils.StratoUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;

/**
 * this class handles the authentication related operations of the server side
 */
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

    /**
     * processes the received authentication message based on the type and content
     * takes action based on the message type.
     *
     * @return whether the received message should terminate the connection
     * @throws IOException from stream and socket operations
     */
    boolean processAuthMessage() throws IOException {
        byte type = commandReader.readByte();
        String payload = new String(readMessage());

        if (type != 0) { // not Auth_Request
            System.out.println("[FATAL] UNKNOWN MESSAGE TYPE");
            return false;
        }

        System.out.println("[Request] Client " + server.getClientPort() + ": " + payload);
        if (inputUsername == null) {
            inputUsername = payload;
            if (isValidUsername(inputUsername)) {
                sendAuthMessage((byte) 1, "Password:");
                return true;
            }
            sendAuthMessage((byte) 2, "Username does not exist.");
            return false;
        }

        inputPassword = payload;
        if (inputPassword.equals(correctPassword)) {
            token = server.registerClient(inputUsername);
            if (token == null) { // Client with this username is already signed in
                sendAuthMessage((byte) 2, "User already signed in.");
                return false;
            }
            sendAuthMessage((byte) 3, "Authenticated successfully!," + token); // send token
            sendAuthMessage((byte) 6, "" + StratoServer.DATA_PORT); // send query connection info
            server.initializeQueryPhase();
            return true;
        }
        if (passwordAttempts > 0) {
            sendAuthMessage((byte) 1, "Incorrect, remaining attempts: " + passwordAttempts + ". Password:");
            passwordAttempts--;
            return true;
        }
        sendAuthMessage((byte) 2, "Incorrect password, out of attempts.");
        return false;
    }

    /**
     * reads the payload of the message from the command socket input stream
     *
     * @return the payload as an array of bytes
     * @throws IOException from stream and socket operations
     */
    private byte[] readMessage() throws IOException {
        int length = commandReader.readInt();
        byte[] message = new byte[length];
        commandReader.readFully(message, 0, message.length);
        return message;
    }

    /**
     * checks the given username with those in the list of users
     *
     * @param name the given username
     * @return whether the given username matches a user in the list of users
     * @throws IOException from stream and socket operations
     */
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

    /**
     * sends an authentication phase method to the server
     * message type not specified because the client can only send Auth_Request messages in this phase.
     *
     * @throws IOException from stream and socket operations
     */
    private void sendAuthMessage(byte type, String payload) throws IOException {
        byte[] message = StratoUtils.makeAuthMessage(type, payload);
        commandWriter.write(message);
    }

    public String getToken() {
        return token;
    }

    /**
     * sends an Auth_Fail message to the client indicating that the connection has timed out
     *
     * @throws IOException from stream and socket operations
     */
    public void sendTimeOutMessage() throws IOException {
        sendAuthMessage((byte) 2, "Connection timed out. Authentication failed.");
    }
}
