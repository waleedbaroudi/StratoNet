package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class StratoClient {

    private ClientAuthModule authModule;
    private ClientQueryModule queryModule;
    private DataOutputStream commandWriter;
    private DataInputStream commandReader;

    public static void main(String[] args) {
        StratoClient client = new StratoClient();
        try {
            client.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void connect() throws IOException {
        Socket authSocket = new Socket("localhost", 5555);
        commandWriter = new DataOutputStream(authSocket.getOutputStream());
        commandReader = new DataInputStream(authSocket.getInputStream());
        authModule = new ClientAuthModule(this, commandReader, commandWriter);

        while (true) {
            if (!receiveMessage())
                break;
        }

        commandWriter.close();
        commandReader.close();
        authSocket.close();
    }

    private boolean receiveMessage() throws IOException {
        byte phase = commandReader.readByte();
        if (phase == 1) {
            return queryModule.processQueryMessage();
        }
        return authModule.processAuthMessage();
    }

    void initializeQueryPhase(int port) throws IOException {
        Socket dataSocket = new Socket("localhost", port);
        DataInputStream dataReader = new DataInputStream(dataSocket.getInputStream());
        queryModule = new ClientQueryModule(this, commandReader, dataReader, commandWriter);
        queryModule.sendQuery();
    }

    public String getToken() {
        return authModule.getToken();
    }
}