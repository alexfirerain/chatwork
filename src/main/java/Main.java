import client.Client;
import server.Server;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        Server chatwork = new Server(Path.of("settings.ini"));
        chatwork.listen();
        Client client_1 = new Client(Path.of("client_1.ini"));
    }
}
