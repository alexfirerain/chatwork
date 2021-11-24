package server;

import common.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Исполняемая в самостоятельном потоке логика работы с конкретным подключением.
 */
public class Connection implements Runnable, AutoCloseable {
    private final Server host;
    private final Dispatcher dispatcher;
    private final Socket socket;
    private final ObjectInputStream messageReceiver;
    private final ObjectOutputStream messageSender;
    private boolean privateMode = true;

    /**
     * Создаёт новое Соединение ассоциированного Сервера над указанным Сокетом.
     * @param host   какой сервер установил это соединение.
     * @param socket собственно соединение с конкретным удалённым адресом.
     * @throws IOException при ошибках получения входящего или исходящего потока.
     */
    public Connection(Server host, Socket socket) throws IOException {
        this.host = host;
        dispatcher = host.users;
        this.socket = socket;
        messageReceiver = new ObjectInputStream(socket.getInputStream());
        messageSender = new ObjectOutputStream(socket.getOutputStream());
    }

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
        // регистрируем участника
        dispatcher.registerUser(this);

        // пока соединено
        while (!socket.isClosed()) {
            // если соединение в приватном режиме, читать из него сообщения будет сам Диспетчер
            if (!privateMode) {
                // иначе: считываем входящие сообщения и передаём их серверу на обработку
                try {
                    operateOn(getMessage());
                } catch (IOException | ClassNotFoundException e) {
                    String error = "Ошибка обработки сообщения: " + e.getMessage();
                    System.out.println(error);
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Записывает в исходящий поток объект сообщения.
     * @param message сообщение, которое отсылается.
     * @throws IOException при невозможности записать в поток.
     */
    public void send(Message message) throws IOException {
        messageSender.writeObject(message);
    }

    /**
     * Получает новый объект сообщения из входящего потока.
     * @return полученное из потока сообщение.
     * @throws IOException если чтение из потока не удаётся.
     * @throws ClassNotFoundException если полученный объект не определяется как сообщение.
     */
    public Message getMessage() throws IOException, ClassNotFoundException {
        return (Message) messageReceiver.readObject();
    }

    /**
     * Обрабатывает полученное на сервер (в открытом режиме) сообщение от участника.
     * @param gotMessage полученное сообщение.
     */
    public void operateOn(Message gotMessage) {
        String sender = gotMessage.getSender();

        switch (gotMessage.getType()) {
            case SERVER_MSG, PRIVATE_MSG -> dispatcher.send(gotMessage);        // SERVER возможен?
            case TXT_MSG -> dispatcher.broadcastFrom(gotMessage);
            case REG_REQUEST -> dispatcher.changeName(sender, this);
            case LIST_REQUEST -> dispatcher.send(usersListMessage(sender));
            case EXIT_REQUEST -> dispatcher.disconnectUser(sender);
            case SHUT_REQUEST -> dispatcher.getShut(sender, host);
        }
    }

    /**
     * Создаёт новое серверное сообщение, содержащее сведения о подключённых
     * в текущий момент участниках, адресуя его тому, кто запросил этот список.
     * @param requesting участник, запросивший список.
     * @return  серверное сообщение со списком подключённых участников.
     */
    private Message usersListMessage(String requesting) {
        StringBuilder report = new StringBuilder("Подключено участников: " + dispatcher.getUsers().size());
        for (String user : dispatcher.getUsers())
            report.append("\n").append(user);
        return Message.fromServer(report.toString(), requesting);
    }

    /**
     * Сообщает, закрыт ли сокетный канал.
     * @return {@code истинно}, если сокет был открыт, а теперь закрыт;
     * {@code ложно}, если сокет открыт, либо ещё не открывался.
     */
    public boolean isClosed() {
        return socket.isClosed();
    }

    @Override
    public void close() throws Exception {
        socket.close();
    }
}
