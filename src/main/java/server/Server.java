package server;

import common.Configurator;
import common.Logger;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final File settingsSource = new File("settings.ini");
    public static final int nickLengthLimit = 15;

    private static final int port_default = 7777;
    private static final byte[] password_default = "0000".getBytes();
    private static final String host_default = "localhost";

    private final ExecutorService connections = Executors.newCachedThreadPool();

    private final boolean LOG_INBOUND;
    private final boolean LOG_OUTBOUND;
    private final boolean LOG_TRANSFERRED;
    private final boolean LOG_ERRORS;


    private final String HOST;
    private final int PORT;
    private final byte[] PASSWORD;

    final Dispatcher users = new Dispatcher();
    private final Logger logger;

    private boolean running;

    /**
     * Создаёт новый Сервер с настройками по умолчанию.
     */
    public Server() {
        HOST = host_default;
        PORT = port_default;
        PASSWORD = password_default;
        LOG_INBOUND = false;
        LOG_OUTBOUND = true;
        LOG_TRANSFERRED = false;
        LOG_ERRORS = false;
        logger = getLogger();
        logger.setLogFile("server.log");
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
        LOG_INBOUND = false;
        LOG_OUTBOUND = true;
        LOG_TRANSFERRED = false;
        LOG_ERRORS = false;
        logger = getLogger();
        logger.setLogFile("server.log");
    }

    /**
     * Создаёт новый Сервер, читая настройки из указанного файла.
     * Если чтение файла или каких-либо настроек не удаётся, назначает
     * все или некоторые значения по умолчанию.
     * @param settingFile адрес файла настроек.
     */
    public Server(Path settingFile) {
        Configurator config = new Configurator(settingFile);
        HOST = config.getStringProperty("HOST").orElse(host_default);
        PORT = config.getIntProperty("PORT").orElse(port_default);
        PASSWORD = (config.getStringProperty("PASSWORD")
                .orElse(Arrays.toString(password_default))).getBytes();
        LOG_INBOUND = config.getBoolProperty("LOG_INBOUND").orElse(false);
        LOG_OUTBOUND = config.getBoolProperty("LOG_INBOUND").orElse(false);
        LOG_TRANSFERRED = config.getBoolProperty("LOG_INBOUND").orElse(false);
        LOG_ERRORS = config.getBoolProperty("LOG_ERRORS").orElse(false);

        logger = getLogger();
        logger.setLogFile("server.log");
    }

    private Logger getLogger() {
        return new Logger(LOG_INBOUND, LOG_OUTBOUND, LOG_TRANSFERRED, LOG_ERRORS);
    }

    /**
     * Сценарий исполнения Сервера: создать новый экземпляр
     * и запустить с него прослушивание на установленном порту.
     * @param args можно передавать имя файла настроек,
     *            но в данном случае оно фиксированное.
     */
    public static void main(String[] args) {
        Server chatwork = new Server(Path.of("settings.ini"));
        chatwork.listen();
    }

    /**
     * Слушает на заданном серверном порту за входящие подключения.
     * Обнаружив таковое, запускает его в новый поток в обойме подключений.
     * А когда пора завершаться, выполнит остановку и всё погасит.
     */
    public void listen() {
        running = true;
        try (final ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (running) {
                try  {
                    Socket socket = serverSocket.accept();
                    System.out.println("connected with " + socket);    // monitor

                    Connection connection = new Connection(this, socket);
                    connections.execute(connection);

                } catch (IOException e) {
                    String error = "Ошибка получения потоков: " + e.getMessage();
                    System.out.println(error);
                    e.printStackTrace();
                }
            }
            users.closeSession();
        } catch (IOException e) {
            String error = "Непредвиденное завершение работы: " + e.getMessage();
            System.out.println(error);
            e.printStackTrace();
        }

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
     * Останавливает сервер, выставляя соответствующий флажок
     * и создавая фантомное подключение для провокации финальной итерации цикла.
     */
    public void stopServer() {
        running = false;
        // виртуальное подключение к серверу, чтобы разблокировать его ожидание на порту
        try {
            new Socket(HOST, PORT).close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public boolean isLOG_INBOUND() {
        return LOG_INBOUND;
    }
}
