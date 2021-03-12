package server;

import utils.StratoUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;

public class StratoServer {

    public static final int AUTH_PORT = 5555;
    public static final int DATA_PORT = 6666;

    ServerSocket authServerSocket, dataServerSocket;

    private final boolean serverRunning;
    private final HashMap<String, String> registeredClients;

    public static void main(String[] args) {
        try {
            new StratoServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public StratoServer() throws IOException {
        authServerSocket = new ServerSocket(AUTH_PORT);
        dataServerSocket = new ServerSocket(DATA_PORT);
        registeredClients = new HashMap<>();
        System.out.println("Server initiated.");
        serverRunning = true;
        while (serverRunning) {
            new StratoClientHandler(authServerSocket.accept(), this).start();
            System.out.println("Client Connected.");
        }
    }

    public String registerClient(String username, String address, int port) {
        String token = generateToken(username);
        String connectionInfo = String.format("%s:%d", address.substring(1), port);

        if (registeredClients.put(token, connectionInfo) == null) // new entry to the map
            return token;
        return null;
    }

    public void unregisterClient(String token) {
        registeredClients.remove(token);
    }

    public String generateToken(String name) {
        String upperName = name.toUpperCase();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < 4; i++)
            token.append((int) upperName.toUpperCase().charAt(i)).append("-");

        token.append((int) (upperName.charAt(upperName.length() - 2))).
                append("-").
                append((int) (upperName.charAt(upperName.length() - 1)));
        return token.toString();
    }

    public Socket getDataSocket() throws IOException {
        return dataServerSocket.accept();
    }

    public boolean isRegisteredToken(String token, String address, int port) {
        String info = address.substring(1) + ":" + port;
        return registeredClients.get(token).equals(info);
    }

    public byte[] requestApod(URL url) throws IOException {
        HttpURLConnection apodConnection = (HttpURLConnection) url.openConnection();
        apodConnection.setRequestMethod("GET");
        apodConnection.setRequestProperty("Content-Type", "application/json");
        int status = apodConnection.getResponseCode();
        System.out.println("RESPONSE CODE: " + status);
        BufferedReader reader = new BufferedReader(new InputStreamReader(apodConnection.getInputStream()));
        String input;
        StringBuffer content = new StringBuffer();

        while ((input = reader.readLine()) != null)
            content.append(input);

        reader.close();
        apodConnection.disconnect();

        String json = content.toString();

        return downloadImage(StratoUtils.extractURL(json));
    }

    private byte[] downloadImage(String url) throws IOException {
        URL imgUrl = new URL(url);
        InputStream imgInput = new BufferedInputStream(imgUrl.openStream());
        ByteArrayOutputStream imgOutput = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length = 0;
        while ((length = imgInput.read(buffer)) != -1) {
            imgOutput.write(buffer, 0, length);
        }
        imgInput.close();
        imgOutput.close();
        return imgOutput.toByteArray();
    }
}


