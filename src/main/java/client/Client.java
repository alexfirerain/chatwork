package client;

import common.Configurator;
import common.Logger;
import common.Message;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
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
    private static final Scanner usersInput = new Scanner(System.in);   // статик или нет ?!
    private static final int POST_SENDING_DELAY = 700;
    private static final String name_default = "";

    private final String HUB;
    private final int PORT;
    private final Path settingFile;
    private final boolean LOG_INBOUND;
    private final boolean LOG_OUTBOUND;
    private final boolean LOG_EVENTS;

    final Logger logger;

    /**
     * Имя участника для использования на сервере.
     */
    private String userName;
    /**
     * Соединение до сервера.
     */
    private Socket connection = null;
    /**
     * Исходящий поток отправлять сообщения на сервер.
     */
    private ObjectOutputStream translator = null;
    /**
     * Поток-приёмник входящих сообщений.
     */
    private Receiver receiver = null;
    /**
     * Сигнализирует совпадение текущего имени пользователя данным на Сервере.
     */
    private volatile boolean registered = false;

    /**
     * Сценарий исполнения Клиента: определить источник настроек и
     * создать на его основе экземпляр, определить начальное имя,
     * затем подключиться к чат-хабу и начать работу.
     * @param args параметры запуска программы из командной строки,
     *             первый из них рассматривается как адрес файла настроек.
     */
    public static void main(String[] args) {
        // Определение файла настроек и запуск на его основе нового клиента
        Client client = new Client(Configurator.identifySource(args, usersInput));
        // Приветствие пользователя, уточнение имени для регистрации
        client.enterUser();
        // Установление соединения с Сервером
        client.connect();

        client.logger.stopLogging();
        System.out.println("END running Client");   // monitor
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
        userName = config.getStringProperty("NAME").orElse(name_default);
        settingFile = filePath;

        LOG_INBOUND = config.getBoolProperty("LOG_INBOUND").orElse(true);
        LOG_OUTBOUND = config.getBoolProperty("LOG_INBOUND").orElse(true);
        LOG_EVENTS = config.getBoolProperty("LOG_EVENTS").orElse(false);
        logger = getLogger();
        logger.setLogFile(Message.isAcceptableName(userName) ? (userName + ".log") : "default_user.log");
    }

    /*
        Методы доступа к полям.
     */
    /**
     * Выставляет в Клиенте флажок, что установленное имя пользователя зарегистрировано
     * на чат-сервере.
     */
    public void setRegistered() {
        registered = true;
    }

    /**
     * Показывает, является ли имя клиента зарегистрированным на чат-сервере.
     * @return значение флажка {@code isRegistered}, то есть {@code истинно},
     * если вызывался {@link #setRegistered()}.
     */
    public boolean isRegistered() {
        return registered;
    }

    /**
     * Устанавливает имя пользователя, используемое для исходящих сообщений.
     * Создаёт лог-файл на установленное имя и заносит в него запись об этом.
     * @param newName устанавливаемое имя.
     */
    public void setUserName(String newName) {
        userName = newName;
        logger.setLogFile(newName + ".log");        // TODO: уточнить проверку на недопустимые символы в файловой системе ↑
        logger.logEvent("установлено имя: " + newName);
    }

    /**
     * Выдаёт имя пользователя, используемое в клиенте в текущий момент.
     * @return строку имя участника.
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Выдаёт ссылку на сокетное соединение, открытое клиентом.
     * @return ссылку на установленный сокет.
     */
    public Socket getConnection() {
        return connection;
    }


    /*
        Методы отправки сообщений.
     */
    /**
     * Отсылает сообщение в исходящий поток и логирует его.
     * @param msg  засылаемое сообщение.
     * @throws IOException если ошибка записи в поток.
     */
    private void push(Message msg) throws IOException {
        translator.writeObject(msg);
        logger.logOutbound(msg);
    }
    /**
     * Отправляет на сервер запрос регистрации того имени,
     * которое текущее в поле {@code userName}.
     * @throws IOException при ошибке исходящего потока.
     */
    private void registeringRequest() throws IOException {
        push(Message.registering(userName));
    }
    /**
     * Формирует из полученного текста новое сообщение от пользователя
     * и засылает его на чат-сервер. Затем замирает на некоторое время.
     * @param inputText введённый пользователем текст.
     * @throws IOException при ошибке исходящего потока.
     */
    private void send(String inputText) throws IOException, InterruptedException {
        push(Message.fromClientInput(inputText, userName));
        // если стоп-сигнал придёт сразу, закрываемся
        Thread.sleep(POST_SENDING_DELAY);
        if(receiver.stopSignReceived()) {
            connection.close();
        }
    }

    /*
     *   Рабочие процедуры.
     */
    /**
     * Подготавливает пользователя к подключению: приветствует, затем интересуется,
     * какое имя использовать для первой попытки регистрации на чат-сервере.
     */
    private void enterUser() {
        System.out.println("Добро пожаловать в программу для общения!");
        // Определение имени для попытки автоматической регистрации:
        String nameToUse = getUserName();
        if (!Message.isAcceptableName(nameToUse)) {
            System.out.println("Введите имя, под которым примете участие в беседе:");
            nameToUse = usersInput.nextLine();
        } else {
            System.out.println("Имя для участия в беседе = " + nameToUse +
                    "\nНажмите <Ввод> для его использования либо введите другое:");
            String inputName = usersInput.nextLine();
            if (Message.isAcceptableName(inputName))
                nameToUse = inputName;
        }
        while (!Message.isAcceptableName(nameToUse)) {
            System.out.println("Введите корректное имя для регистрации:");
            nameToUse = usersInput.nextLine();
        }
        setUserName(nameToUse);
    }

    /**
     * Основной рабочий цикл Клиента. Подключается к указанному в настройках Чат-Хабу,
     * инициализирует Приёмник для обработки поступающих от сервера сообщений,
     * запускает процедуру регистрации в переговорной и начинает отправлять сообщения
     * на основе введённого в консоль текста, пока соединение не будет закрыто.
     */
    private void connect() {
        String error = null;
        try {
            connection = new Socket(HUB, PORT);
            logger.logEvent("Установлено соединение с " + connection);
            translator = new ObjectOutputStream(connection.getOutputStream());

            // в самотекущем Приёмнике слушать входящие сообщения
            receiver = new Receiver(this);
            receiver.start();

            // запрос регистрации подготовленного имени
            registeringRequest();
            // цикл до подтверждения регистрации
            while (!connection.isClosed() && !registered) {
                String inputName = usersInput.nextLine();

                // если к моменту ввода пользователя регистрация уже состоялась,
                // введённое отправляется как обычное сообщение
                if (registered) {
                    send(inputName);
                } else {
                    if (Message.isAcceptableName(inputName))
                        setUserName(inputName);
                    registeringRequest();
                }
            }
            saveSettings();

            // основной рабочий цикл
            while (!connection.isClosed()) {
                send(usersInput.nextLine());        // чтобы разорвать это ожидание в конце
                // в методе .send() есть задержка получить, возможно, сообщение о конце сеанса
            }

        } catch (ConnectException e) {
            error = "Сервер не отвечает: " + e.getMessage();

        } catch (UnknownHostException e) {
            error = "Хаб для подключения не обнаружен: " + e.getMessage();
            e.printStackTrace();
        } catch (IOException e) {
            error = "Ошибка соединения: " + e.getMessage();
            e.printStackTrace();
        } catch (InterruptedException e) {
            error = "Прерывание: " + e.getMessage();
            e.printStackTrace();
        } finally {
            if (error != null) {
                System.out.println(error);
                logger.logEvent(error);
            }
            String event = "Завершение работы чат-клиента.";
            logger.logEvent(event);
            System.out.println(event);
            if (receiver != null)
                receiver.interrupt();
//            logger.stopLogging();
        }

    }

    /*
         Вспомогательные функции.
     */
    /**
     * Сбрасывает текущие настройки в связанный файл настроек.
     */
    protected void saveSettings() {
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
     * Вспомогательная функция, создающая экземпляр логировщика для клиента
     * с теми настройками протоколирования, которые уже заданы в константах сервера.
     * @return экземпляр логера с описанными настройками.
     */
    private Logger getLogger() {
        return new Logger(LOG_INBOUND, LOG_OUTBOUND, false, LOG_EVENTS);
    }


}
