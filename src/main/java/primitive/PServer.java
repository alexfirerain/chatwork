package primitive;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;

public class PServer {
    private final String host;
    private final int port;
    private volatile boolean isOn;
    private ServerSocket serverSocket;

    public PServer(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        serverSocket = new ServerSocket(port);
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        PServer server = new PServer("localhost", 7777);
        server.listen();
    }

    private void listen() throws IOException, ClassNotFoundException {
            System.out.println("listening started");
            isOn = true;
            while (isOn) {
                Socket connection = serverSocket.accept();
                System.out.println("connected with " + connection.getRemoteSocketAddress());
                PMessage hello = PMessage.hello();

                ObjectOutputStream out = new ObjectOutputStream(connection.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(connection.getInputStream());

                System.out.println("streams got");

                out.writeObject(hello);
                out.flush();

                while (!connection.isClosed()) {
                    PMessage got = (PMessage) in.readObject();
                    System.out.println("got: " + got);
                    out.writeObject(new PMessage("we got: " + got));
                    out.flush();
                }

            }

    }
}
