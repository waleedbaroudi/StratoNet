package client;

import utils.InvalidTokenException;
import utils.StratoUtils;

import javax.swing.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;

/**
 * this class handles the query related operations of the client side
 */
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

    /**
     * takes client input, forms a query and sends it through the output stream of the command socket
     *
     * @return whether the user chose to end connection
     * @throws IOException           from stream and socket operations
     * @throws InvalidTokenException if the token to be appended to the query does not
     *                               follow the normal token format
     */
    boolean sendQuery() throws IOException, InvalidTokenException {
        System.out.println("Choose API (APOD or Insight) [1 / 2], or [-1] to disconnect:");
        byte option = input.nextByte();
        input.nextLine();
        switch (option) {
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

        recentQuery = new String[]{"" + option, param};
        commandWriter.write(StratoUtils.makeQueryMessage(client.getToken(), option, param));
        return true;
    }

    /**
     * processes the received query-phase message based on the type and content
     * takes action based on the message type.
     *
     * @return whether the received message should terminate the connection
     * @throws IOException from stream and socket operations
     */
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

    /**
     * processes the received data based on the data type (Image or JSON String)
     *
     * @throws IOException from stream and socket operations
     */
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
            processImage(data);
        else // Insight data
            processJSONObject(data);

        // send acknowledge message
        commandWriter.write(StratoUtils.makeQueryMessage(client.getToken(), (byte) 5, hashcode));
    }

    /**
     * prompts the to choose an action for the image
     * @param data the image as a byte array
     * @throws IOException from stream and socket operations
     */
    private void processImage(byte[] data) throws IOException {
        System.out.println("Save or Display image? [0 / 1]");
        String choice = input.nextLine();
        if (choice.equals("0"))
            saveImage(data);
        else
            displayImage(data);

    }

    /**
     * displays the image in a JFrame
     * @param data the image as a byte array
     */
    private void displayImage(byte[] data) {
        JFrame imageFrame = new JFrame("APOD");
        imageFrame.getContentPane().add(new JLabel(new ImageIcon(data)));
        imageFrame.pack();
        imageFrame.setLocationRelativeTo(null);
        imageFrame.setVisible(true);
        imageFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    /**
     * Skips the bytes containing the token, as a token check is not necessary at this point.
     *
     * @return whether the token bytes were skipped successfully
     * @throws IOException from stream and socket operations
     */
    private boolean skipToken() throws IOException {
        if (commandReader.skipBytes(StratoUtils.TOKEN_LENGTH) != StratoUtils.TOKEN_LENGTH) {// skip token bytes
            // token bytes were not skipped fully
            System.out.println("FATA: error reading message");
            return false;
        }
        return true;
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
     * checks the validity of the hashcode of the received file
     *
     * @param receivedHash hash received through the command socket
     * @param data         received file
     * @param type         type of received file
     * @return whether the received hashcode matches that of the received file
     */
    private boolean verifyDataHash(String receivedHash, byte[] data, byte type) {
        String receivedFileHash = (type == 1 ? "img-" : "str-") + Arrays.hashCode(data);
        return receivedHash.equals(receivedFileHash);
    }

    /**
     * resends a request of the last query to the server.
     *
     * @throws IOException from stream and socket operations
     */
    private void requestRetransmit() throws IOException {
        System.out.println("File mismatch: incorrect file hashcode, requesting retransmit..");
        commandWriter.write(StratoUtils.makeQueryMessage(client.getToken(), Byte.parseByte(recentQuery[0]), recentQuery[1]));
    }

    /**
     * @param data the received image
     * @throws IOException from stream and socket operations
     */
    private void saveImage(byte[] data) throws IOException {
        System.out.println("Enter image name: ");
        String name = input.nextLine();
        StratoUtils.saveImage(data, name);
        System.out.println("image saved.");
    }

    /**
     * process the received JSON object and print relevant data
     *
     * @param data the received JSON object
     */
    private void processJSONObject(byte[] data) {
        String pressure = new String(data);
        String[] values = StratoUtils.getPressureValues(pressure);
        for (String value : values)
            System.out.println(value.trim());
    }

    /**
     * indicates disconnecting from server, sends a Query_Disc message to the server
     *
     * @throws IOException from stream and socket operations
     */
    public void disconnect() throws IOException {
        System.out.println("Disconnecting from server..");
        commandWriter.write(StratoUtils.makeQueryMessage(client.getToken(), (byte) 6, ""));
    }
}
