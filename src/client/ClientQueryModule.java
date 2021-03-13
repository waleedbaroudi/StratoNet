package client;

import utils.InvalidTokenException;
import utils.StratoUtils;

import java.io.*;
import java.util.Arrays;
import java.util.Scanner;

class ClientQueryModule {
    private final StratoClient client;
    private final DataInputStream commandReader;
    private final DataInputStream dataReader;
    private final DataOutputStream commandWriter;
    private final Scanner input;

    private String[] recentQuery;

    public ClientQueryModule(StratoClient client, DataInputStream commandReader, DataInputStream dataReader, DataOutputStream commandWriter) {
        this.client = client;
        this.commandReader = commandReader;
        this.dataReader = dataReader;
        this.commandWriter = commandWriter;
        input = new Scanner(System.in);
    }


    boolean sendQuery() throws IOException, InvalidTokenException {
        System.out.println("Choose API (APOD or Insight) [1 / 2], or [-1] to disconnect:");
        byte api = input.nextByte();
        input.nextLine();
        switch (api) {
            case -1:
                disconnect();
                return false;
            case 1:
                System.out.println("Enter date [yyyy-mm-dd]:");
                break;
            case 2:
                System.out.println("Enter sol number [1-7]:");
                break;
            default:
                System.out.println("Invalid command");
                return false;
        }
        String param = input.nextLine();

        recentQuery = new String[]{"" + api, param};
        commandWriter.write(StratoUtils.makeQueryMessage(client.getToken(), api, param));
        return true;
    }

    boolean processQueryMessage() throws IOException {
        if (!skipToken())
            return false;
        byte type = commandReader.readByte();
        String payload = new String(readMessage());
        switch (type) {
            case 0: //hash
                receiveData(payload);
                return sendQuery();
            case 3: // info
                System.out.println("[INFO] " + payload);
                return true;
            case 4: // fail
                System.out.println("[FAIL] " + payload);
                return false;
            default:
                System.out.println("[FATAL] UNKNOWN MESSAGE TYPE");
                return false;
        }
    }

    private void receiveData(String hashcode) throws IOException {
        byte type = dataReader.readByte();
        int length = dataReader.readInt();
        byte[] data = new byte[length];
        dataReader.readFully(data, 0, data.length);
        if (!verifyDataHash(hashcode, data, type)) {
            requestRetransmit();
            return;
        }
        if (type == 1) // APOD data
            saveImage(data);
        else // Insight data
            processJSONObject(data);

        // send acknowledge message
        commandWriter.write(StratoUtils.makeQueryMessage(client.getToken(), (byte) 5, hashcode));
    }

    private boolean skipToken() throws IOException {
        if (commandReader.skipBytes(StratoUtils.TOKEN_LENGTH) != StratoUtils.TOKEN_LENGTH) {// skip token bytes
            // token bytes were not skipped fully
            System.out.println("FATA: error reading message");
            return false;
        }
        return true;
    }

    private byte[] readMessage() throws IOException {
        int length = commandReader.readInt();
        byte[] message = new byte[length];
        commandReader.readFully(message, 0, message.length);
        return message;
    }

    private boolean verifyDataHash(String hash, byte[] data, byte type) {
        String receivedFileHash = (type == 1 ? "img-" : "str-") + Arrays.hashCode(data);
        return hash.equals(receivedFileHash);
    }

    private void requestRetransmit() throws IOException {
        System.out.println("File mismatch: incorrect file hashcode, requesting retransmit..");
        commandWriter.write(StratoUtils.makeQueryMessage(client.getToken(), Byte.parseByte(recentQuery[0]), recentQuery[1]));
    }

    private void saveImage(byte[] data) throws IOException { // todo: move to utils?
        System.out.println("Enter image name: ");
        String name = input.nextLine();
        System.out.println("saving image..");
        FileOutputStream saveStream = new FileOutputStream(System.getProperty("user.dir") + File.separator + name + ".jpg");
        saveStream.write(data);
        saveStream.close();
        System.out.println("image saved.");
    }

    private void processJSONObject(byte[] data) {
        String pressure = new String(data);
        String[] values = StratoUtils.getPressureValues(pressure);
        for (String value : values)
            System.out.println(value.trim());
    }

    public void disconnect() throws IOException {
        System.out.println("Disconnecting from server..");
        commandWriter.write(StratoUtils.makeQueryMessage(client.getToken(), (byte) 6, ""));
    }
}
