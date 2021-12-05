package client;

import common.Configurator;
import common.Logger;
import common.Message;
import common.MessageType;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Клиентская программа инициализирует соединение с сервером на основе
 * файла настроек и пользовательского ввода, регистрирует пользователя,
 * затем запускает отдельный поток для приёма сообщений,
 * а сама занимается отправлением сообщений от пользователя.
 */
public class Client {
    private static final String host_default = "localhost";
    private static final int port_default = 7777;
    private static final Scanner usersInput = new Scanner(System.in);

    private final String HUB;
    private final int PORT;
    private final Path settingFile;
    private final boolean LOG_INBOUND;
    private final boolean LOG_OUTBOUND;
    private final boolean LOG_EVENTS;
    final Logger logger;

    private String userName;
    private Socket connection = null;
    private ObjectOutputStream messagesOut = null;
    /**
     * Сигнализирует совпадение текущего имени пользователя данным на Сервере.
     */
    private volatile boolean isRegistered = false;

    /**
     * Сценарий исполнения Клиента: определить источник настроек и
     * создать на его основе экземпляр, определить начальное имя,
     * затем подключиться к чат-хабу и начать работу.
     * @param args параметры запуска программы из командной строки,
     *             первый из них рассматривается как адрес файла настроек.
     */
    public static void main(String[] args) {
        // Определение файла настроек и запуск на его основе нового клиента
        Client client = new Client(identifySource(args));
        // Приветствие пользователя, уточнение имени для регистрации
        client.enterUser();
        // Установление соединения с Сервером
        client.connect();

        System.out.println("END running Client");   // monitor
    }

    /**
     * Определяет путь к файлу настроек, который должен использоваться.<p>
     * В качестве аргумента предполагается массив аргументов, с которым запущена программа.
     * Если программа запущена без аргументов, или первый аргумент не является адресом существующего файла,
     * запрашивает у пользователя ввод адреса, пока он не окажется именем существующего файла.
     * При этом является ли указанный файл действительно корректным файлом настроек – не проверяется.
     * @param args массив строковых аргументов (переданный программе при запуске).
     * @return путь к файлу, полученный из аргумента командной строки или из ввода пользователя.
     */
    private static Path identifySource(String[] args) {
        String filePath;
        do if (args.length >= 1 && Files.isRegularFile(Path.of(args[0]))) {
            filePath = args[0];
        } else {
            System.out.println("Источник настроек не обнаружен, введите имя файла вручную:");
            filePath = usersInput.nextLine();
        } while (!Files.isRegularFile(Path.of(filePath)));
        System.out.println("Настройки загружены из " + filePath);
        return Path.of(filePath);
    }

    /**
     * Подготавливает пользователя к подключению: приветствует, затем интересуется,
     * какое имя использовать для первой попытки регистрации на чат-сервере.
     */
    private void enterUser() {
        System.out.println("Добро пожаловать в программу для общения!");
        // Определение имени для попытки автоматической регистрации:
        String nameToUse = getUserName();
        if (!isAcceptableName(nameToUse)) {
            System.out.println("Введите имя, под которым примете участие в беседе:");
            nameToUse = usersInput.nextLine();
        } else {
            System.out.println("Имя для участия в беседе = " + nameToUse +
                    "\nНажмите <Ввод> для его использования либо введите другое:");
            String inputName = usersInput.nextLine();
            if (isAcceptableName(inputName))
                nameToUse = inputName;
        }
        while (!isAcceptableName(nameToUse)) {
            System.out.println("Введите имя для регистрации:");
            nameToUse = usersInput.nextLine();
        }
        setUserName(nameToUse);
    }

    /**
     * Создаёт новый Клиент на основе данных из файла настроек,
     * либо, если файл или какие-то отдельные настройки не найдены,
     * на основе значений по умолчанию.
     * @param filePath путь к файлу настроек.
     */
    public Client(Path filePath) {
        Configurator config = new Configurator(filePath);
        HUB = config.getStringProperty("HOST").orElse(host_default);
        PORT = config.getIntProperty("PORT").orElse(port_default);
        userName = config.getStringProperty("NAME").orElse("");
        settingFile = filePath;

        LOG_INBOUND = config.getBoolProperty("LOG_INBOUND").orElse(true);
        LOG_OUTBOUND = config.getBoolProperty("LOG_INBOUND").orElse(true);
        LOG_EVENTS = config.getBoolProperty("LOG_EVENTS").orElse(false);
        logger = getLogger();
        logger.setLogFile(isAcceptableName(userName) ? (userName + ".log") : "default_user.log");
    }

    /**
     * Вспомогательная функция, создающая экземпляр логировщика
     * с установленными в конструкторе настройками протоколирования.
     * @return экземпляр логера с описанными настройками.
     */
    private Logger getLogger() {
        return new Logger(LOG_INBOUND, LOG_OUTBOUND, false, LOG_EVENTS);
    }

    public void connect() {
        Receiver receiver = null;
        try {
            connection = new Socket(HUB, PORT);
            logger.logEvent("Установлено соединение с " + connection);
            messagesOut = new ObjectOutputStream(connection.getOutputStream());

            // в самотекущем Приёмнике слушать входящие сообщения
            receiver = new Receiver(this);
            receiver.start();

            // запрос регистрации подготовленного имени
            registeringRequest();

            // цикл до подтверждения регистрации
            while (!connection.isClosed() && !isRegistered) {
                String inputName = usersInput.nextLine();

                // если к моменту ввода пользователя регистрация уже состоялась,
                // введённое отправляется как обычное сообщение
                if (isRegistered) {
                    send(inputName);
                } else {
                    if (isAcceptableName(inputName))
                        setUserName(inputName);
                    registeringRequest();
                }
            }
            saveSettings();

            // основной рабочий цикл
            while (!connection.isClosed()) {
                send(usersInput.nextLine());        // нужно как-то разорвать это ожидание в конце
            }

//            logger.logEvent("Завершение работы чат-клиента.");
//            receiver.interrupt();
//            logger.stopLogging();

        } catch (UnknownHostException e) {
            String error = "Хаб для подключения не обнаружен: " + e.getMessage();
            System.out.println(error);
            logger.logEvent(error);
            e.printStackTrace();
        } catch (IOException e) {
            String error = "Ошибка соединения: " + e.getMessage();
            System.out.println(error);
            logger.logEvent(error);
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            logger.logEvent("Завершение работы чат-клиента.");
            if (receiver != null)
                receiver.interrupt();
            logger.stopLogging();
        }

    }

    /**
     * Формирует из полученного текста новое сообщение
     * и засылает его на чат-сервер.
     * @param inputText введённый пользователем текст.
     * @throws IOException при ошибке исходящего потока.
     */
    private void send(String inputText) throws IOException, InterruptedException {
        Message messageToSend = Message.fromClientInput(inputText, userName);
        messagesOut.writeObject(messageToSend);
        logger.logOutbound(messageToSend);
        if (messageToSend.getType() == MessageType.EXIT_REQUEST) {
            Thread.sleep(3000);
            connection.close();
        }
    }

    /**
     * Выставляет в Клиенте флажок, что установленное имя пользователя зарегистрировано
     * на чат-сервере.
     */
    public void setRegistered() {
        isRegistered = true;
    }

    /**
     * Показывает, является ли имя клиента зарегистрированным на чат-сервере.
     * @return значение флажка {@code isRegistered}, то есть {@code истинно},
     * если вызывался {@code .setRegistered()}.
     */
    public boolean isRegistered() {
        return isRegistered;
    }

    /**
     * Отправляет на сервер запрос регистрации того имени,
     * которое текущее в поле {@code userName}.
     * @throws IOException при ошибке исходящего потока.
     */
    public void registeringRequest() throws IOException {
        Message requestForRegistration = Message.registering(userName);
        messagesOut.writeObject(requestForRegistration);
        logger.logOutbound(requestForRegistration);
    }

    /**
     * Сбрасывает текущие настройки в связанный файл настроек.
     */
    void saveSettings() {
        Map<String, String> settings = new HashMap<>();
        settings.put("HUB", HUB);
        settings.put("PORT", String.valueOf(PORT));
        if (userName != null)
            settings.put("NAME", userName);
        settings.put("LOG_INBOUND", String.valueOf(LOG_INBOUND));
        settings.put("LOG_OUTBOUND", String.valueOf(LOG_OUTBOUND));
        settings.put("LOG_EVENTS", String.valueOf(LOG_EVENTS));
        Configurator.writeSettings(settings, settingFile);      // вывести обработку ошибки сюда, чтобы логировать?
    }

    /**
     * Сообщает, является ли указанная строка существующей и не пустой.
     * @param name строка.
     * @return  {@code истинно}, если строка содержит хотя бы один значимый символ.
     */
    private static boolean isAcceptableName(String name) {      // вот сюда-то и нужно добавить проверку на буквенный диапазон
        return name != null && !name.isBlank();
    }

    /**
     * Устанавливает имя пользователя, используемое для исходящих сообщений.
     * @param newName устанавливаемое имя.
     */
    public void setUserName(String newName) {
        userName = newName;
        logger.setLogFile(newName + ".log");            // TODO: проверку, чтоб в имени не было недопустимых символов
        logger.logEvent("установлено имя: " + newName);
    }

    public String getUserName() {
        return userName;
    }

    public Socket getConnection() {
        return connection;
    }


}
