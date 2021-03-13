package client;

import utils.StratoUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Scanner;

class ClientAuthModule {

    private final StratoClient client;
    private final DataInputStream reader;
    private final DataOutputStream writer;
    private final Scanner input;

    private String token;

    public ClientAuthModule(StratoClient client, DataInputStream reader, DataOutputStream writer) {
        this.client = client;
        this.reader = reader;
        this.writer = writer;
        input = new Scanner(System.in);
    }

    boolean processAuthMessage() throws IOException {
        byte type = reader.readByte();
        String payload = new String(readMessage());

        switch (type) {
            case 1: // Auth_Challenge
                System.out.println("[CHALLENGE] " + payload);
                sendAuthMessage();
                return true;
            case 2: // Auth_Fail
                System.out.println("[FAIL] " + payload);
                return false;
            case 3: // Auth_Success
                String[] responses = payload.split(",");
                System.out.println("[SUCCESS] " + responses[0]);
                token = responses[1];
                return true;
            case 5: //Auth_Info
                System.out.println("[INFO] " + payload);
                return true;
            case 6: //Auth_Connect
                return client.initializeQueryPhase(Integer.parseInt(payload));
            default:
                System.out.println("[FATAL] UNKNOWN MESSAGE TYPE");
                return false;
        }
    }

    private void sendAuthMessage() throws IOException {
        String payload = input.nextLine();
        writer.write(StratoUtils.makeAuthMessage((byte) 0, payload));
    }

    public String getToken() {
        return token;
    }

    private byte[] readMessage() throws IOException {
        int length = reader.readInt();
        byte[] message = new byte[length];
        reader.readFully(message, 0, message.length);
        return message;
    }


}
