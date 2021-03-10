import java.io.IOException;
import java.net.ServerSocket;

public class StratoServer {

    private boolean serverRunning;

    public static void main(String[] args) {
        try {
            new StratoServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public StratoServer() throws IOException {
        ServerSocket authSS = new ServerSocket(5555);
        System.out.println("Server initiated.");
        serverRunning = true;
        while (serverRunning) {
            new StratoClientHandler(authSS.accept()).start();
            System.out.println("Client Connected.");
        }
    }
}


