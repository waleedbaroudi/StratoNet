package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

    public String apiRequest(URL url) throws IOException {
        HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
        httpConnection.setRequestMethod("GET");
        httpConnection.setRequestProperty("Content-Type", "application/json");
        int status = httpConnection.getResponseCode();
        System.out.println("RESPONSE CODE: " + status);
        BufferedReader reader = new BufferedReader(new InputStreamReader(httpConnection.getInputStream()));
        String input;
        StringBuffer content = new StringBuffer();

        while ((input = reader.readLine()) != null)
            content.append(input);

        reader.close();
        httpConnection.disconnect();

        return content.toString();
    }

}


