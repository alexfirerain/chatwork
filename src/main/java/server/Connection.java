package server;

import common.Logger;
import common.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

import static server.TextConstants.*;

/**
 * Исполняемая в самостоятельном потоке логика работы сервера с конкретным подключением.
 */
public class Connection implements Runnable, AutoCloseable {
    /**
     * Сервер, установивший это Соединение.
     */
    private final Server host;
    /**
     * Диспетчер сервера, знающий о зарегистрированных участниках и
     * организующий их коммуникацию.
     */
    private final Dispatcher dispatcher;
    /**
     * Сокетное соединение, обёрткой для которого служит этот объект.
     */
    private final Socket socket;
    /**
     * Логировщик сервера, используемый для записи событий и сообщений.
     */
    private final Logger logger;
    /**
     * Входящий объектный поток от сокета.
     */
    private ObjectInputStream messageReceiver;
    /**
     * Исходящий объектный поток на сокет.
     */
    private ObjectOutputStream messageSender;
    /**
     * Показатель, находится ли Соединение в локальном режиме, то есть что обработка
     * полученного сообщения не передаётся Диспетчеру, а обрабатывается в локальном методе.
     * С начала установления соединения оно находится в локальном режиме и
     * выходит из него по успешном окончании регистрации имени участника для себя.
     * Также переход в локальный режим требуется, когда Соединение ожидает подтверждения пароля.
     */
    private boolean localMode = true;

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

    /**
     * Сценарий исполнения Соединения: получить из сокета исходящий и
     * входящий потоки, запустить процедуру регистрации, затем начать
     * ожидание входящих сообщений и, когда-если в глобальном режиме,
     * передавать их Диспетчеру.
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
                // если соединение в локальном режиме: новое сообщение обрабатывается локальным методом
                if (!localMode) {
                    // если в глобальном: считываем входящее сообщение и передаём его Диспетчеру на обработку
                    try {
                        dispatcher.operateOn(receiveMessage(), this);

                    } catch (SocketException e) {
                        String error = "Соединение закрыто: " + e.getMessage();
                        System.out.println(error);
                        logger.logEvent(error);
//                        e.printStackTrace();
//                        socket.close();
                    } catch (IOException | ClassNotFoundException e) {
                        String error = "Ошибка обработки сообщения: " + e.getMessage();
                        System.out.println(error);
                        logger.logEvent(error);
                        e.printStackTrace();
//                        break;
                    }
                }
            }

        } catch (IOException e) {
            String error = "Отсутствие потоков: " + e.getMessage();
            System.out.println(error);
            logger.logEvent(error);
//            e.printStackTrace();
        }
//        finally {
//            try {
//                close();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }

        System.out.println("END running Connection " + this);   // monitor
    }

    /**
     * Проводит регистрацию имени пользователя для данного соединения.
     */
    public void registerUser() {
        try {
            sendMessage(Message.fromServer("Соединение с ... " + host.HOST));
            String sender = receiveMessage().getSender();
            while(!dispatcher.addUser(sender, this)) {
                sendMessage(Message.fromServer(REGISTRATION_WARNING.formatted(sender)));
                sender = receiveMessage().getSender();
            }
            setGlobalMode();
            dispatcher.greetUser(sender);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Записывает в исходящий поток объект сообщения.
     * @param message сообщение, которое отсылается.
     * @throws IOException при невозможности записать в поток.
     */
    public void sendMessage(Message message) throws IOException {
        messageSender.writeObject(message);

//        if (message.isServerMessage() || logger.noLoggingTransferred())
//            logger.logOutbound(message);
//        else
//            logger.logTransferred(message);
    }

    /**
     * Дожидается и отдаёт новый объект сообщения из входящего потока.
     * Логирует, если это сообщение-запрос.
     * @return полученное из потока сообщение.
     * @throws IOException если чтение из потока не удаётся.
     * @throws ClassNotFoundException если полученный объект не определяется как сообщение.
     */
    public Message receiveMessage() throws IOException, ClassNotFoundException {
        Message gotMessage = (Message) messageReceiver.readObject();
        if (gotMessage.isRequest())
            logger.logInbound(gotMessage);
        return gotMessage;
    }

    /**
     * Процедура подтверждения команды остановки: запрашивает пароль
     * у запросившего выключение участника и, если получает совпадающий
     * с установленным на сервере, запускает остановку сервера.
     */
    public void getShut() {
        setLocalMode();
        String requesting = dispatcher.getUserForConnection(this);
        byte[] gotPassword = new byte[0];
        try {
            sendMessage(Message.fromServer(PASSWORD_REQUEST, requesting));
            gotPassword = receiveMessage().getMessage().getBytes();    //TODO: принимая пароль, подавить логирование
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        setGlobalMode();
        if (host.wordPasses(gotPassword)) {
            host.stopServer();
        }
    }

    /*
        Вспомогательныя функции.
     */
    /**
     * Переводит Соединение в локальный режим.
     */
    public void setLocalMode() {
        localMode = true;
    }
    /**
     * Переводит Соединение в глобальный режим.
     */
    public void setGlobalMode() {
        localMode = false;
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
        return "%s@%s:%d".formatted(
                dispatcher.getUserForConnection(this),
                socket.getInetAddress(),
                socket.getPort());
    }
}
