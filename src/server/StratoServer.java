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

    // port numbers for command and data server sockets
    public static final int AUTH_PORT = 5555;
    static final int DATA_PORT = 6666; // package visibility only
    // because the client shouldn't know about it directly

    ServerSocket authServerSocket, dataServerSocket;

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
        while (true) {
            new StratoClientHandler(authServerSocket.accept(), this).start();
            System.out.println("Client Connected.");
        }
    }

    /**
     * registers a given client to the map of logged in users
     *
     * @param username username of the client
     * @param address  IP address of the client
     * @param port     port number of the client
     * @return the token for the registered user, or null if the user is already logged in
     */
    public String registerClient(String username, String address, int port) {
        String token = generateToken(username);
        String connectionInfo = String.format("%s:%d", address.substring(1), port);

        if (registeredClients.put(token, connectionInfo) == null) // new entry to the map
            return token;
        return null;
    }

    /**
     * unregisters a user from the map of logged-in users
     *
     * @param token token of the user to be removed
     */
    public void unregisterClient(String token) {
        registeredClients.remove(token);
    }

    /**
     * generates a token for a client based on their username
     *
     * @param name the username user to generate a token
     * @return the token
     */
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

    /**
     * starts a data socket and returns once a client connects to it
     *
     * @return the created data socket
     * @throws IOException from stream and socket operations
     */
    public Socket getDataSocket() throws IOException {
        return dataServerSocket.accept();
    }

    /**
     * checks whether a client with the given info is registered
     *
     * @param token   the token of the client
     * @param address the IP address of the client
     * @param port    the port number of the client
     * @return whether the token is registered or not
     */
    public boolean isRegisteredToken(String token, String address, int port) {
        String info = address.substring(1) + ":" + port;
        String registeredInfo = registeredClients.get(token);
        return registeredInfo != null && registeredInfo.equals(info);
    }

    /**
     * performs an Http request
     *
     * @param url the url to which the request is to be sent
     * @return the response if the request was successful, null otherwise
     * @throws IOException from stream and socket operations
     */
    public String apiRequest(URL url) throws IOException {
        HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
        httpConnection.setRequestMethod("GET");
        httpConnection.setRequestProperty("Content-Type", "application/json");
        int status = httpConnection.getResponseCode();
        System.out.println("RESPONSE CODE: " + status);
        if (status != 200)
            return null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(httpConnection.getInputStream()));
        String input;
        StringBuilder content = new StringBuilder();

        while ((input = reader.readLine()) != null)
            content.append(input);

        reader.close();
        httpConnection.disconnect();

        return content.toString();
    }
}


