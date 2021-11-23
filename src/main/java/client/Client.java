package client;

import common.Configurator;

import java.net.Socket;
import java.nio.file.Path;

public class Client {
    private static final String host_default = "localhost";
    private static final int port_default = 7777;
    private static final String name_default = "Имя_участника";
    // при запуске спрашивает имя файла настроек и загружает их из него {сайт:порт, имя_пользователя}
    // после выбора имени пользователя пытается сохранить файл с таким именем,
    // а также создаёт лог-файл с таким именем (если он ещё не существует)

    private final String HUB;
    private final int PORT;
    private final Path settingFile;

    private Socket connection;
    private String userName;


//    public static void main(String[] args) {
//        Client client = Client.onSettingsFile("client.ini");
//    }
    private Client(String hub, int port, String name, Path filePath) {
        HUB = hub;
        PORT = port;
        userName = name;
        settingFile = filePath;
    }


    public Client(Path filePath) {
        Configurator config = new Configurator(filePath);
        HUB = config.getStringProperty("HOST").orElse(host_default);
        PORT = config.getIntProperty("PORT").orElse(port_default);
        userName = config.getStringProperty("NAME").orElse(name_default);
        settingFile = filePath;
    }

    public void connect() {

    }

}
