package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;

public class StratoServer {

    private boolean serverRunning;

    private HashMap<Long, String> registeredClients;

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

    public long registerClient(String username, String address, int port) {
        long token = generateToken(username);
        String connectionInfo = String.format("%s:%d", address, port);
        if (registeredClients.put(token, connectionInfo) == null) // new entry to the map
            return token;
        return -1;
    }

    public void unregisterClient(long token) {
        registeredClients.remove(token);
    }

    public long generateToken(String name) {
        int index = 1;
        int token = 0;
        for (int i = 0; i < name.length(); i++) {
            token += (name.charAt(i) * index);
            index *= 10;
        }
        System.out.println("Token created for name [" + name + "]: " + token);
        return token;
    }
}


