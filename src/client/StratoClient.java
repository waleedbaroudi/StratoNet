package client;

import utils.InvalidTokenException;
import utils.StratoUtils;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;

public class StratoClient {

    private Socket dataSocket;

    private DataOutputStream commandWriter;
    private DataInputStream commandReader, dataReader;
    private Scanner input;

    String token;

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
        input = new Scanner(System.in);


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
            byte[] receivedToken = new byte[StratoUtils.TOKEN_LENGTH]; // TODO: remove this (redundant)
            commandReader.readFully(receivedToken, 0, receivedToken.length);
            byte type = commandReader.readByte();
            int length = commandReader.readInt();
            byte[] message = new byte[length];
            commandReader.readFully(message, 0, message.length);
            return processQueryMessage(message, type);
        }
        byte type = commandReader.readByte();
        int length = commandReader.readInt();
        byte[] message = new byte[length];
        commandReader.readFully(message, 0, message.length);
        return processAuthMessage(message, type);
    }

    private boolean processQueryMessage(byte[] message, byte type) throws IOException {
        String payload = new String(message);

        switch (type) {
            case 0: //hash
                receiveData(payload);
                sendQuery();
                return true;
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

    private boolean processAuthMessage(byte[] message, byte type) throws IOException {
        String payload = new String(message);

        switch (type) {
            case 1: // Auth_Challenge
                System.out.println("[CHALLENGE] " + payload);
                sendMessage();
                return true;
            case 2: // Auth_Fail
                System.out.println("[FAIL] " + payload);
                return false;
            case 3: // Auth_Success
                String[] responses = payload.split(",");
                System.out.println("[SUCCESS] " + responses[0]);
                token = responses[1];
                sendQuery();
                return true;
            case 5: //Auth_Info
                System.out.println("[INFO] " + payload);
                return true;
            case 6: //Auth_Connect
                initializeQueryPhase(Integer.parseInt(payload));
                return true;
            default:
                System.out.println("[FATAL] UNKNOWN MESSAGE TYPE");
                return false;
        }
    }

    private void sendMessage() throws IOException {
        String payload = input.nextLine();
        commandWriter.write(StratoUtils.makeAuthMessage((byte) 0, payload));
    }

    private void initializeQueryPhase(int port) throws IOException {
        dataSocket = new Socket("localhost", port);
        dataReader = new DataInputStream(dataSocket.getInputStream());
    }

    private void sendQuery() throws IOException, InvalidTokenException {
        System.out.println("Choose API (APOD or Insight) [1 / 2]:");
        byte api = input.nextByte();
        input.nextLine();
        if (api == 1) // todo: change this to byte
            System.out.println("Enter date [yyyy-mm-dd]:");
        else
            System.out.println("Enter sol number [1-7]:");
        String param = input.nextLine();

        commandWriter.write(StratoUtils.makeQueryMessage(token, api, param));
    }

    private void receiveData(String hashcode) throws IOException {
        byte type = dataReader.readByte();
        int length = dataReader.readInt();
        byte[] data = new byte[length];
        dataReader.readFully(data, 0, data.length);
        if (!verifyDataHash(hashcode, data, type)) {
            //request retransmit
            System.out.println("FILE MISMATCH: WRONG HASH");
            return;
        }
        if (type == 1) // APOD data
            downloadImage(data);
        else // Insight data
            processJSONObject(data);
    }

    private void downloadImage(byte[] data) throws IOException {
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

    private boolean verifyDataHash(String hash, byte[] data, byte type) {
        String receivedFileHash = (type == 1 ? "img-" : "str-") + Arrays.hashCode(data);
        return hash.equals(receivedFileHash);
    }


}