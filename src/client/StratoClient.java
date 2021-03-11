package client;

import utils.StratoUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class StratoClient {

    private Socket dataSocket;

    private DataOutputStream commandWriter;
    private DataInputStream commandReader, dataReader;
    private Scanner input;

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
            System.out.println("WRONG PHASE");
            return false;
        }
        byte type = commandReader.readByte();
        int length = commandReader.readInt();
        byte[] message = new byte[length];
        commandReader.readFully(message, 0, message.length);
        return processMessage(message, type);
    }

    private boolean processMessage(byte[] message, byte type) throws IOException {
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
                System.out.println("[SUCCESS] " + payload);
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
        System.out.println("Data socket and stream initialized with port " + port);
    }
}