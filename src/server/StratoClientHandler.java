package server;

import utils.StratoUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;

public class StratoClientHandler extends Thread {
    private final Socket commandSocket;
    private Socket dataSocket;
    private final StratoServer server;

    private int currentPhase = 0;

    DataInputStream commandReader;
    DataOutputStream commandWriter;
    DataOutputStream dataWriter;

    private ServerAuthModule authModule;
    private ServerQueryModule queryModule;

    public StratoClientHandler(Socket commandSocket, StratoServer server) {
        this.commandSocket = commandSocket;
        this.server = server;
    }

    public void run() {
        try {
            commandWriter = new DataOutputStream(commandSocket.getOutputStream());
            commandReader = new DataInputStream(commandSocket.getInputStream());
            commandSocket.setSoTimeout(10000);
            authModule = new ServerAuthModule(this, commandReader, commandWriter);

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
            System.err.println("Lost connection with client");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            disconnectClient();
        }
    }

    private void closeConnection() {
        try {
            commandWriter.close();
            commandReader.close();
            commandSocket.close();
            if (dataWriter != null) {
                dataWriter.close();
                dataSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean receiveMessage() throws IOException {
        byte phase = commandReader.readByte();
        if (phase != currentPhase) {
            rejectMessage();
            return false;
        }
        if (phase == 0) { // auth
            return authModule.processAuthMessage();
        }
        if (phase == 1) { // query
            return queryModule.processQueryMessage();
        }
        return false;
    }


    private void sendMessage(byte phase, byte type, String payload) throws IOException {
        if (phase == 0) // auth
            commandWriter.write(StratoUtils.makeAuthMessage(type, payload));
        else  // query
            commandWriter.write(StratoUtils.makeQueryMessage(authModule.getToken(), type, payload));
    }


    private void sendTimeOutMessage() {
        System.out.println("Client " + commandSocket.getPort() + " timed out.");
        try {
            if (currentPhase == 0)
                authModule.sendTimeOutMessage();
            else
                queryModule.sendTimeOutMessage();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void initializeQueryPhase() throws IOException {
        currentPhase = 1;
        dataSocket = server.getDataSocket();
        dataWriter = new DataOutputStream(dataSocket.getOutputStream());
        queryModule = new ServerQueryModule(this, commandReader, dataWriter, commandWriter);
        System.out.println("user connected to data socket");
    }


    void setProcessing(boolean isProcessing) throws IOException {
        commandSocket.setSoTimeout(isProcessing ? 0 : 10000);
    }

    private void rejectMessage() throws IOException {
        if (currentPhase == 0)
            sendMessage((byte) currentPhase, (byte) 2, "Authentication phase already passed.");
        else
            sendMessage((byte) currentPhase, (byte) 4, "Access Denied: unauthorized user");
    }

    public String registerClient(String inputUsername) {
        return server.registerClient(inputUsername, commandSocket.getInetAddress().toString(), commandSocket.getPort());
    }

    public boolean isRegisteredToken(String token) {
        return server.isRegisteredToken(token, commandSocket.getInetAddress().toString(), commandSocket.getPort());
    }

    public String getToken() {
        return authModule.getToken();
    }

    public String apiRequest(URL url) throws IOException {
        return server.apiRequest(url);
    }

    void disconnectClient() {
        System.err.println("Client with port " + commandSocket.getPort() + " disconnected");
        server.unregisterClient(authModule.getToken());
    }
}