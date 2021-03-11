package client;

import utils.StratoUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class StratoClient {

    DataOutputStream writer;
    DataInputStream reader;
    Scanner input;

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
        writer = new DataOutputStream(authSocket.getOutputStream());
        reader = new DataInputStream(authSocket.getInputStream());
        input = new Scanner(System.in);


        while (true) {
            if (!receiveMessage())
                break;
        }

        writer.close();
        reader.close();
        authSocket.close();
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
            default:
                System.out.println("[FATAL] UNKNOWN MESSAGE TYPE");
                return false;
        }
    }

    private void sendMessage() throws IOException {
        String payload = input.nextLine();
        writer.write(StratoUtils.makeAuthMessage((byte) 0, payload));
    }
}