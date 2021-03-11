package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;

public class StratoServer {

    private boolean serverRunning;

    private final HashMap<String, String> registeredClients;

    public static void main(String[] args) {
        try {
            new StratoServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public StratoServer() throws IOException {
        ServerSocket authSS = new ServerSocket(5555);
        registeredClients = new HashMap<>();
        System.out.println("Server initiated.");
        serverRunning = true;
        while (serverRunning) {
            new StratoClientHandler(authSS.accept(), this).start();
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
        StringBuilder token = new StringBuilder();
        token.append((int) name.toUpperCase().charAt(0));
        for (int i = 1; i < name.length(); i++) {
            token.append("-").append((int) name.toUpperCase().charAt(i));
        }
        return token.toString();
    }
}


