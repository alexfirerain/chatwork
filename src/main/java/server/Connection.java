package server;

import common.Logger;
import common.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

import static common.MessageType.SERVER_MSG;

/**
 * Исполняемая в самостоятельном потоке логика работы сервера с конкретным подключением.
 */
public class Connection implements Runnable, AutoCloseable {
    private static final String WARN_TXT = "Зарегистрировать такое имя не получилось!";
    private static final String CLOSING_TXT = "Сервер завершает работу!";
    private static final String PASSWORD_REQUEST = "Введите пароль для управления сервером";
    private static final String PROMPT_TEXT = ("Добро пожаловать в переговорную комнату!\n" +
            "Пишите в беседу свои сообщения и читайте сообщения других участников.");
    /**
     * Сервер, установивший это Соединение.
     */
    private final Server host;
    /**
     * Диспетчер сервера, знающий о зарегистрированных участниках и
     * организующий их коммуникацию.
     */
    private final Dispatcher dispatcher;
    private final Socket socket;
    private final Logger logger;

    private ObjectInputStream messageReceiver;
    private ObjectOutputStream messageSender;
    private boolean privateMode = true;

    /**
     * Создаёт новое Соединение ассоциированного Сервера над указанным Сокетом.
     * @param host   какой сервер установил это соединение.
     * @param socket собственно соединение с конкретным удалённым адресом.
     */
    public Connection(Server host, Socket socket) {
        this.host = host;
        dispatcher = host.users;
        logger = host.logger;
        this.socket = socket;
    }

    // нужен ли вообще где-то?
    public void enterPrivateMode() {
        privateMode = true;
    }
    public void exitPrivateMode() {
        privateMode = false;
    }
    /**
     * Когда объект, воплощающий интерфейс {@code Runnable}, используется
     * для создания нити, запуск ({@code start}) нити означает вызов метода
     * {@code run} этого объекта в отдельно исполняемом потоке.
     * <p>
     * Общая договорённость насчёт метода {@code run} такова, что он может
     * предпринимать какое угодно действие.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        try (socket) {
            messageSender = new ObjectOutputStream(socket.getOutputStream());
            messageReceiver = new ObjectInputStream(socket.getInputStream());

            // регистрируем участника
            registerUser();

            // пока соединено
            while (!socket.isClosed()) {
                // если соединение в приватном режиме, читать из него сообщения будет сам Диспетчер
                if (!privateMode) {
                    // иначе: считываем входящие сообщения и передаём их серверу на обработку
                    try {
                        dispatcher.operateOn(receiveMessage(), this);

                    } catch (SocketException e) {
                        String error = "Соединение закрыто: " + e.getMessage();
                        System.out.println(error);
                        logger.logEvent(error);
                        e.printStackTrace();
                        socket.close();
                    } catch (IOException | ClassNotFoundException e) {
                        String error = "Ошибка обработки сообщения: " + e.getMessage();
                        System.out.println(error);
                        logger.logEvent(error);
                        e.printStackTrace();
                        break;
                    }
                }
            }

        } catch (IOException e) {
            String error = "ошибка получения потоков: " + e.getMessage();
            System.out.println(error);
            logger.logEvent(error);
            e.printStackTrace();
        }
//        finally {
//            try {
//                close();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }

        System.out.println("END running Connection");   // monitor
    }

    /**
     * Записывает в исходящий поток объект сообщения.
     * @param message сообщение, которое отсылается.
     * @throws IOException при невозможности записать в поток.
     */
    public void sendMessage(Message message) throws IOException {
        messageSender.writeObject(message);

        if (message.getType() == SERVER_MSG || !logger.isLoggingTransferred())
            logger.logOutbound(message);
        else
            logger.logTransferred(message);
    }

    /**
     * Получает новый объект сообщения из входящего потока.
     * @return полученное из потока сообщение.
     * @throws IOException если чтение из потока не удаётся.
     * @throws ClassNotFoundException если полученный объект не определяется как сообщение.
     */
    public Message receiveMessage() throws IOException, ClassNotFoundException {
        Message gotMessage = (Message) messageReceiver.readObject();
        if (gotMessage.getType().ordinal() > 2)
            logger.logInbound(gotMessage);
        return gotMessage;
    }

//    /**
//     * Обрабатывает полученное на сервер (в открытом режиме) сообщение от участника.
//     * @param gotMessage полученное сообщение.
//     */
//    public void operateOn(Message gotMessage) {
//        String sender = gotMessage.getSender();
//
//        switch (gotMessage.getType()) {
//            case SERVER_MSG, PRIVATE_MSG -> dispatcher.send(gotMessage);        // SERVER возможен? / при регистрации? / нет, убрать его
//            case TXT_MSG -> dispatcher.forward(gotMessage);
//            case REG_REQUEST -> dispatcher.changeName(sender, this);
//            case LIST_REQUEST -> dispatcher.sendUserList(sender);
//            case EXIT_REQUEST -> dispatcher.goodbyeUser(sender);
//            case SHUT_REQUEST -> dispatcher.getShut(sender, host);
//        }
//    }

    /**
     * Проводит регистрацию имени пользователя для данного соединения.
     */
    public void registerUser() {
        try {
            sendMessage(Message.fromServer(PROMPT_TEXT));   // не нужно, коль скоро провоцирует подключение клиент!
            String sender = receiveMessage().getSender();
            while(!dispatcher.addUser(sender, this)) {
                sendMessage(Message.fromServer(WARN_TXT));
                sender = receiveMessage().getSender();
            }
            exitPrivateMode();
            dispatcher.greetUser(sender);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Запрашивает (в приватном режиме) пароль у запросившего выключение участника
     * и, если получает пароль, совпадающий с установленным на сервере,
     * запускает остановку сервера.
     */
    public void getShut() {
        enterPrivateMode();
        String requesting = dispatcher.getUserForConnection(this);
        byte[] gotPassword = new byte[0];
        try {
            sendMessage(Message.fromServer(PASSWORD_REQUEST, requesting));
            gotPassword = receiveMessage().getMessage().getBytes();    //TODO: принимая пароль, подавить логирование
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        exitPrivateMode();
        if (host.wordPasses(gotPassword)) {
//            dispatcher.sendStopSignal(requesting, CLOSING_TXT);
            host.stopServer();
        }
    }

    /**
     * Сообщает, закрыт ли сокетный канал.
     * @return {@code истинно}, если сокет был открыт, а теперь закрыт;
     * {@code ложно}, если сокет открыт либо ещё не открывался.
     */
    public boolean isClosed() {
        return socket.isClosed();
    }

    @Override
    public void close() throws Exception {
        socket.close();
    }

    @Override
    public String toString() {
        return dispatcher.getUserForConnection(this) + "@" + socket.getInetAddress() + ":" + socket.getLocalPort();
    }
}
