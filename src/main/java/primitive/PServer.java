package primitive;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class PServer {
    private final String host;
    private final int port;
    private volatile boolean isOn;

    public PServer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static void main(String[] args) {
        PServer server = new PServer("localhost", 7777);
        server.listen();
    }

    private void listen() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("port opened");
            isOn = true;
            while (isOn) {
                try (Socket connection = serverSocket.accept();
                     ObjectInputStream in = new ObjectInputStream(connection.getInputStream());
                     ObjectOutputStream out = new ObjectOutputStream(connection.getOutputStream())) {
                    System.out.println("connected with " + connection.getRemoteSocketAddress());

                    try {
                        System.out.println("streams got");

                        PMessage hello = PMessage.hello();
                        out.writeObject(hello);
                        out.flush();

                        while (!connection.isClosed()) {
                            PMessage got = (PMessage) in.readObject();
                            System.out.println("got: " + got);
                            out.writeObject(new PMessage("we got: " + got));
                        }



                    } catch (IOException | ClassNotFoundException e) {
                        System.out.println("running error: " + e.getMessage());
                        e.printStackTrace();
                    }


                } catch (IOException e) {
                    System.out.println("socket error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.out.println("server socket error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
