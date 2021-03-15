package client;

import server.StratoServer;

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

    /**r
     * initiates socket to the server as well as associated input/output streams and auth module
     * starts the interaction loop with the server
     *
     * @throws IOException from stream and socket operations
     */
    public void connect() throws IOException {

        Socket authSocket = new Socket("localhost", StratoServer.AUTH_PORT);
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

    /**
     * receives a message and directs it to the appropriate module
     *
     * @return whether the received message should terminate connection
     * @throws IOException from stream and socket operations
     */
    private boolean receiveMessage() throws IOException {
        byte phase = commandReader.readByte(); // listening (since readByte() blocks)
        if (phase == 1) {
            return queryModule.processQueryMessage();
        }
        return authModule.processAuthMessage();
    }

    /**
     * connects to the data socket, initializes the input stream from the data socket and the query module
     *
     * @param port the received port for the data socket
     * @return whether the client chose to terminate connection
     * @throws IOException from stream and socket operations
     */
    boolean initializeQueryPhase(int port) throws IOException {
        Socket dataSocket = new Socket("localhost", port);
        DataInputStream dataReader = new DataInputStream(dataSocket.getInputStream());
        queryModule = new ClientQueryModule(this, commandReader, dataReader, commandWriter);
        return queryModule.sendQuery();
    }

    public String getToken() {
        return authModule.getToken();
    }

}