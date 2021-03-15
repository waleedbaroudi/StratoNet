package server;

import utils.StratoUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * this class handles the query related operations of the server side
 */
public class ServerQueryModule {
    private final StratoClientHandler server;
    private final DataInputStream commandReader;
    private final DataOutputStream dataWriter;
    private final DataOutputStream commandWriter;

    public ServerQueryModule(StratoClientHandler server, DataInputStream commandReader, DataOutputStream dataWriter, DataOutputStream commandWriter) {
        this.server = server;
        this.commandReader = commandReader;
        this.dataWriter = dataWriter;
        this.commandWriter = commandWriter;
    }

    /**
     * processes the received query-phase message based on the type and content
     * takes action based on the message type.
     *
     * @return whether the received message should terminate the connection
     * @throws IOException from stream and socket operations
     */
    boolean processQueryMessage() throws IOException {
        byte[] receivedToken = readToken();
        byte type = commandReader.readByte();
        String message = new String(readMessage());
        if (!server.isRegisteredToken(new String(receivedToken))) {
            sendMessage((byte) 4, "Access Denied: invalid token");
            return false;
        }

        switch (type) {
            case 1:
                handleApodRequest(message);
                return true;
            case 2:
                handleInsightRequest(message);
                return true;
            case 5:
                System.out.println("Acknowledged: " + message);
                // stop processing state
                server.setProcessing(false);
                return true;
            case 6:
                server.disconnectClient();
                return false;
            default:
                sendMessage((byte) 4, "Unknown Query Operation");
                return false;
        }
    }

    /**
     * sends a command message to the client
     *
     * @param type    type of the message
     * @param payload content of the message
     * @throws IOException from stream and socket operations
     */
    private void sendMessage(byte type, String payload) throws IOException {
        commandWriter.write(StratoUtils.makeQueryMessage(server.getToken(), type, payload));
    }

    /**
     * reads the token of the message from the command socket input stream
     *
     * @return the payload as an array of bytes
     * @throws IOException from stream and socket operations
     */
    private byte[] readToken() throws IOException {
        byte[] token = new byte[StratoUtils.TOKEN_LENGTH];
        commandReader.readFully(token, 0, token.length);
        return token;
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
     * handles a client's query to the APOD API
     *
     * @param param the parameter given by the user (desired picture date)
     * @throws IOException from stream and socket operations
     */
    private void handleApodRequest(String param) throws IOException {
        sendMessage((byte) 3, "Processing request..");
        server.setProcessing(true);
        URL url = new URL(StratoUtils.APOD_URL + param);
        String response = server.apiRequest(url);
        if (response == null) {
            sendMessage((byte) 4, "Invalid request: no results found.");
            return;
        }
        String imageUrl = StratoUtils.extractURL(response);
        if (!imageUrl.endsWith(".jpg")) { // no image url in the returned json object
            sendMessage((byte) 4, "No image found with the given date.");
            return;
        }
        // image url found
        byte[] image = StratoUtils.downloadImage(imageUrl);
        sendMessage((byte) 0, StratoUtils.generateHash(1, image));
        sendData((byte) 1, image.length, image);
    }

    /**
     * handles a client's query to the Insight API
     *
     * @param param the parameter given by the user (desired sol number)
     * @throws IOException from stream and socket operations
     */
    private void handleInsightRequest(String param) throws IOException {
        sendMessage((byte) 3, "Processing request..");
        server.setProcessing(true);
        URL url = new URL(StratoUtils.INSIGHT_URL);
        String response = server.apiRequest(url);
        if (response == null) {
            sendMessage((byte) 4, "Invalid request: no results found.");
            return;
        }
        String[] solPREList = StratoUtils.extractPREObjects(response);
        byte[] data = solPREList[Integer.parseInt(param) - 1].getBytes(StandardCharsets.UTF_8);
        sendMessage((byte) 0, StratoUtils.generateHash(2, data));
        sendData((byte) 2, data.length, data);
    }

    /**
     * sends the data retrieved from an API to the client
     *
     * @param type   data type (image or JSON object)
     * @param length size of the data (in bytes)
     * @param data   the data (image or JSON Object) as an array of bytes
     * @throws IOException from stream and socket operations
     */
    private void sendData(byte type, int length, byte[] data) throws IOException {
        dataWriter.write(type);
        dataWriter.write(StratoUtils.intToByte(length));
        dataWriter.write(data);
    }

    /**
     * sends a Query_Fail message to the client indicating that the connection has timed out
     *
     * @throws IOException from stream and socket operations
     */
    public void sendTimeOutMessage() throws IOException {
        sendMessage((byte) 4, "Connection timed out, terminating session..");
    }
}
