package client;

import common.Configurator;
import common.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
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
    private Receiver receiver;
    private volatile boolean isRegistered = false;
    private Socket socket;


    public static void main(String[] args) {

        // Определение файла настроек и запуск на его основе нового клиента:
        String filePath;
        do {
            if (args.length >= 1 && Files.isRegularFile(Path.of(args[0]))) {
                filePath = args[0];
            } else {
                System.out.println("Источник настроек не обнаружен, введите имя файла вручную:");
                filePath = usersInput.nextLine();
            }
        } while (!Files.isRegularFile(Path.of(filePath)));

        System.out.println("Настройки загружены из " + filePath);
        Client client = new Client(Path.of(filePath));
        client.welcome();

        // Определение имени для попытки автоматической регистрации:
        String nameToUse = client.userName;
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
        client.userName = nameToUse;

        // Установление соединения с Сервером.
        client.connect();


    }

    private void welcome() {
        System.out.println("Добро пожаловать в программу для общения!");
    }

    public void setRegistered() {
        isRegistered = true;
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
        try (Socket connection = new Socket(HUB, PORT);
             ObjectInputStream messagesIn = new ObjectInputStream(connection.getInputStream());
             ObjectOutputStream messagesOut = new ObjectOutputStream(connection.getOutputStream())) {

            socket = connection;
            receiver = new Receiver(this, messagesIn);
            receiver.start();
            messagesOut.writeObject(Message.registering(userName));
            while (!isRegistered) {
                messagesOut.writeObject(Message.registering(usersInput.nextLine()));
            }
            saveSettings();
            while (!connection.isClosed()) {
                messagesOut.writeObject(Message.fromClientInput(usersInput.nextLine(), userName));
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

    /**
     * Сообщает, является ли указанная строка существующей и не пустой.
     * @param name строка.
     * @return  {@code истинно}, если строка содержит хотя бы один значимый символ.
     */
    private static boolean isAcceptableName(String name) {
        return name != null && !name.isBlank();
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
    }

    public Socket getSocket() {
        return socket;
    }
}
