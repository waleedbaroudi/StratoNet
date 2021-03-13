package server;

import utils.StratoUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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
                sendMessage((byte) 4, "Unknown API Operation");
                return false;
        }
    }

    private void sendMessage(byte type, String payload) throws IOException {
        commandWriter.write(StratoUtils.makeQueryMessage(server.getToken(), type, payload));
    }

    private byte[] readToken() throws IOException {
        byte[] token = new byte[StratoUtils.TOKEN_LENGTH];
        commandReader.readFully(token, 0, token.length);
        return token;
    }

    private byte[] readMessage() throws IOException {
        int length = commandReader.readInt();
        byte[] message = new byte[length];
        commandReader.readFully(message, 0, message.length);
        return message;
    }

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
        if (imageUrl == null) { // no image url in the returned json object
            sendMessage((byte) 4, "No image found with the given date.");
            return;
        }
        // image url found
        byte[] image = StratoUtils.downloadImage(imageUrl);
        sendMessage((byte) 0, StratoUtils.generateHash(1, image));
        sendData((byte) 1, image.length, image);
    }

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

    private void sendData(byte type, int length, byte[] data) throws IOException {
        dataWriter.write(type);
        dataWriter.write(StratoUtils.intToByte(length));
        dataWriter.write(data);
    }


    public void sendTimeOutMessage() throws IOException {
        sendMessage((byte) 4, "Connection timed out, terminating session..");
    }
}
