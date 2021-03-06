package server;

import common.Configurator;
import common.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Server {
    private static final Path settingsSource = Path.of("settings.ini");
    private static final int port_default = 7777;
    private static final byte[] password_default = "0000".getBytes();
    private static final String host_default = "localhost";

    /**
     * Адрес сервера.
     */
    public final String HOST;
    /**
     * Серверный порт, на который ожидаются подключения.
     */
    public final int PORT;
    /**
     * Пароль, который открывает доступ к настройкам сервера
     * (в данной реализации – к команде на остановку).
     */
    private final byte[] PASSWORD;
    /**
     * Обойма потоков, обрабатывающих подключения.
     */
    private final ExecutorService connections = Executors.newCachedThreadPool();
    /**
     * Производится ли протоколирование принятых на сервер сообщений.
     */
    private final boolean LOG_INBOUND;
    /**
     * Производится ли протоколирование исходящих с сервера сообщений.
     */
    private final boolean LOG_OUTBOUND;
    /**
     * Производится ли протоколирование переправленных сервером сообщений.
     */
    private final boolean LOG_TRANSFERRED;
    /**
     * Производится ли протоколирование ошибок и событий на сервере.
     */
    private final boolean LOG_EVENTS;

    /**
     * Диспетчер подключённых пользователей и коммуникации сообщений между ними.
     */
    final Dispatcher users;
    /**
     * Логировщик сообщений и событий, используемый сервером.
     */
    final Logger logger;

    /**
     * Работает ли сервер на приём подключений.
     */
    private volatile boolean listening;       // нужно ли ей быть волатильной?

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
        LOG_EVENTS = false;
        logger = getLogger();
        logger.setLogFile("server.log");
        users = new Dispatcher(this);
    }

    /**
     * Создаёт новый Сервер с явно указанными настройками. Настройки логировщика принимаются по умолчанию.
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
        LOG_EVENTS = false;
        logger = getLogger();
        logger.setLogFile("server.log");
        users = new Dispatcher(this);
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
        LOG_OUTBOUND = config.getBoolProperty("LOG_OUTBOUND").orElse(true);
        LOG_TRANSFERRED = config.getBoolProperty("LOG_TRANSFERRED").orElse(false);
        LOG_EVENTS = config.getBoolProperty("LOG_EVENTS").orElse(false);

        logger = getLogger();
        logger.setLogFile("server.log");                // адрес тоже может быть вынесен в настройки
        users = new Dispatcher(this);
    }

    /**
     * Вспомогательная функция, создающая экземпляр логера
     * с установленными в конструкторе настройками логирования.
     * @return экземпляр логера с описанными настройками.
     */
    private Logger getLogger() {
        return new Logger(LOG_INBOUND, LOG_OUTBOUND, LOG_TRANSFERRED, LOG_EVENTS);
    }

    /**
     * Сценарий исполнения Сервера: создать новый экземпляр
     * и запустить с него прослушивание на установленном порту.
     * @param args можно передавать имя файла настроек,
     *            но в данном случае оно фиксированное.
     */
    public static void main(String[] args) {
        Server chatwork = new Server(settingsSource);
        chatwork.listen();
        chatwork.exit();
        System.out.println("END running Server");       // monitor
    }

    /**
     * Слушает на заданном серверном порту за входящие подключения.
     * Обнаружив таковое, запускает его в новый поток в обойме подключений.
     * Повторяет это, пока флажок {@code listening} {@code = истинно}.
     */
    private void listen() {
        listening = true;
        try (final ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (listening) {
                try  {
                    Socket socket = serverSocket.accept();
                    logger.logEvent("Соединение с " + socket);
                    connections.execute(new Connection(this, socket));

                } catch (IOException e) {
                    String error = "Ошибка получения соединения: " + e.getMessage();
                    System.out.println(error);
                    logger.logEvent(error);
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            String error = "Непредвиденное завершение работы: " + e.getMessage();
            System.out.println(error);
            logger.logEvent(error);
            e.printStackTrace();
        }
    }

    /**
     * Выполняет процедуру остановки Сервера: завершает сессию,
     * закрывает соединения и останавливает логировщик.
     */
    private void exit() {
        users.closeSession();
        try {
            if (connections.awaitTermination(3, TimeUnit.SECONDS))
                logger.logEvent("потоки соединений завершены");    //
            else
                logger.logEvent("потоки за три секунды не зашли");  //
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        logger.stopLogging();
    }


    /**
     * Проверяет, подходит ли полученный пароль, и, если да, то останавливает сервер
     * путём выход из цикла прослушивания, выставляя соответствующий флажок
     * и создавая фантомное подключение для провокации финальной итерации.
     * @param gotPassword байты, соответствующие паролю.
     */
    public void stopServer(byte[] gotPassword) {
        if (!Arrays.equals(PASSWORD, gotPassword)) return;
        listening = false;
        // виртуальное подключение к серверу, чтобы разблокировать его ожидание на порту
        try {
            new Socket(HOST, PORT).close();
        } catch (IOException e) {
            logger.logEvent(e.getMessage());
            e.printStackTrace();
        }
    }

}
