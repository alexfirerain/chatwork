package connection;

import java.net.Socket;

public class Connection implements AutoCloseable {
    private final Socket socket;

    public Connection(Socket socket) {
        this.socket = socket;
    }


    @Override
    public void close() throws Exception {
        socket.close();

    }
}
