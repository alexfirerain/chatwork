package server;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final File settingsSource = new File("settings.ini");
    public static final int nickLengthLimit = 15;
    private final static int port_default = 7777;
    private final static byte[] password_default = "0000".getBytes();
    private final static String host_default = "localhost";

    private final ExecutorService connections = Executors.newCachedThreadPool();
    final Dispatcher users = new Dispatcher();

    private final String HOST;
    private final int PORT;
    private final byte[] PASSWORD;
    private boolean running;

    /**
     * Создаёт новый Сервер с настройками по умолчанию.
     */
    public Server() {
        HOST = host_default;
        PORT = port_default;
        PASSWORD = password_default;
    }

    /**
     * Создаёт новый Сервер с явно указанными настройками.
     * @param host     имя хоста.
     * @param port     серверный порт.
     * @param password пароль для выключения.
     */
    public Server(String host, int port, byte[] password) {
        HOST = host;
        PORT = port;
        PASSWORD = password;
    }

    /**
     * Создаёт новый Сервер, читая настройки из указанного файла.
     * Если чтение файла или каких-либо настроек не удаётся, назначает
     * все или некоторые значения по умолчанию.
     * @param settingFile адрес файла настроек.
     * @return новый Сервер, попытавшись инициализировать его настройки из файла.
     */
    public static Server onSettingsFile(Path settingFile) {
        String host = host_default;
        int port = port_default;
        byte[] password = password_default;
        try {
            Map<String, String> settings = readSettings(settingFile);

            String host_value = settings.get("HOST");
            if (host_value != null && !host_value.isBlank())
                host = host_value;

            try {
                if (settings.get("PORT") != null)
                    port = Integer.parseInt(settings.get("PORT"));
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }

            String password_value = settings.get("PASSWORD");
            if (password_value != null && !password_value.isBlank())
                password = password_value.getBytes();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return new Server(host, port, password);
    }

    public static void main(String[] args) {
        Server chatwork = Server.onSettingsFile(Path.of("settings.ini"));
        chatwork.listen();
    }

    private void listen() {
        running = true;
        try (final ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (running) {
                final Socket socket = serverSocket.accept();
                connections.execute(new Connection(this, socket));
            }
        } catch (IOException e) {
            System.out.println("Прослушивание порта завершилось: " + e.getMessage());
            e.printStackTrace();
        }
        users.closeSession();
        connections.shutdownNow();
    }

    /**
     * Сообщает, совпадает ли переданный пароль с установленным.
     * @param password переданный пароль.
     * @return {@code истинно}, если переданный пароль побайтово совпал с заданным.
     */
    public boolean wordPasses(byte[] password) {
        return Arrays.equals(PASSWORD, password);
    }

    /**
     * Читает настройки из файла и представляет их в виде карты "параметр-значение".
     * @param settingsSource адрес читаемого файла.
     * @return  карту настроек, трактуя строки, разделённые ";", как "пара ключ-значение",
     * а внутри пары трактуя то, что идёт до первого "=", как ключ, а после него - как значение.
     * @throws IOException при ошибке чтения файла.
     */
    private static Map<String, String> readSettings(Path settingsSource) throws IOException {
        Map<String,String> settingsMap = new HashMap<>();
        String source = Files.readString(settingsSource);
        String[] lines = source.split(";");
        for (String line : lines) {
            int delim = line.indexOf("=");
            settingsMap.put(line.substring(0, delim).strip(),
                    line.substring(delim + 1).strip());
        }
        return settingsMap;
    }

    public void stopServer() {
        running = false;
        // виртуальное подключение к серверу, чтобы разблокировать его ожидание на порту
        try {
            new Socket(HOST, PORT).close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }






}
