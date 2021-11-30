package client;

import common.Configurator;
import common.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
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

    // при запуске спрашивает имя файла настроек и загружает их из него {сайт:порт, имя_пользователя}
    // после выбора имени пользователя пытается сохранить файл с таким именем,
    // а также создаёт лог-файл с таким именем (если он ещё не существует)

    private final String HUB;
    private final int PORT;
    private final Path settingFile;
    private String userName;

    private Socket connection = null;
    private ObjectOutputStream messagesOut = null;

    private volatile boolean isRegistered = false;

    public static void main(String[] args) {

        // Определение файла настроек и запуск на его основе нового клиента
        Client client = new Client(identifySource(args));

        // Приветствие пользователя, уточнение имени для регистрации
        client.enterUser();

        // Установление соединения с Сервером
        client.connect();


    }

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
        userName = config.getStringProperty("NAME").orElse("");
        settingFile = filePath;
    }

    public void connect() {
        try {
            connection = new Socket(HUB, PORT);
            messagesOut = new ObjectOutputStream(connection.getOutputStream());
//            ObjectInputStream messagesIn = new ObjectInputStream(connection.getInputStream());

            System.out.println("connect: " + connection);    // monitor


            // в самотекущем Приёмнике слушать входящие сообщения
            Receiver receiver = new Receiver(this);
            receiver.start();

            // запрос регистрации подготовленного имени
            registeringRequest();
            // цикл до подтверждения регистрации
            while (!connection.isClosed() && !isRegistered) {
                String inputName = usersInput.nextLine();

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
                send(usersInput.nextLine());
                // TODO: сохранение в настройках нового имени в случае его смены по ходу общения
            }

            receiver.interrupt();

        } catch (UnknownHostException e) {
            String error = "Хаб для подключения не обнаружен: " + e.getMessage();
            System.out.println(error);
            e.printStackTrace();
        } catch (IOException e) {
            String error = "Ошибка соединения: " + e.getMessage();
            System.out.println(error);
            e.printStackTrace();
        }

    }

    private void send(String inputText) throws IOException {
        messagesOut.writeObject(Message.fromClientInput(inputText, userName));
    }

    /**
     * Выставляет в клиенте флажок, что установленное имя пользователя зарегистрировано
     * на чат-сервере.
     */
    public void setRegistered() {
        System.out.println(this + " set as registered");    // monitor
        isRegistered = true;
    }

    /**
     * Показывает, является ли имя клиента зарегистрированным на чат-сервере.
     * @return значение поля {@code isRegistered}, то есть {@code истинно},
     * если вызывался {@code .setRegistered()}.
     */
    public boolean isRegistered() {
        return isRegistered;
    }

    public void registeringRequest() throws IOException {
        messagesOut.writeObject(Message.registering(userName));
//        messagesOut.flush();
    }

    /**
     * Сбрасывает текущие настройки в связанный файл настроек.
     */
    private void saveSettings() {
        Map<String, String> settings = new HashMap<>();
        settings.put("HUB", HUB);
        settings.put("PORT", String.valueOf(PORT));
        settings.put("NAME", userName);
        Configurator.writeSettings(settings, settingFile);
        System.out.println("name in settings saved");           // monitor
    }

    public String getUserName() {
        return userName;
    }

    public Socket getConnection() {
        return connection;
    }

    /**
     * Сообщает, является ли указанная строка существующей и не пустой.
     * @param name строка.
     * @return  {@code истинно}, если строка содержит хотя бы один значимый символ.
     */
    private static boolean isAcceptableName(String name) {
        return name != null && !name.isBlank();
    }

    public void setUserName(String newName) {
        userName = newName;
    }
}
