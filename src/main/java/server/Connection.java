package server;

import connection.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class Connection implements Runnable, AutoCloseable {
    private final Server host;
    private final Dispatcher dispatcher;
    private final Socket socket;
    private final ObjectInputStream messageReceiver;
    private final ObjectOutputStream messageSender;

    public Connection(Server host, Socket socket) throws IOException {
        this.host = host;
        dispatcher = host.users;
        this.socket = socket;
        messageReceiver = new ObjectInputStream(socket.getInputStream());
        messageSender = new ObjectOutputStream(socket.getOutputStream());
    }


    @Override
    public void close() throws Exception {
        socket.close();

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

        // пока соединено, считываем входящие сообщения и передаём их серверу на обработку.
        while (!socket.isClosed()) {
            try {
                operateOn(getMessage());
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public void send(Message message) throws IOException {
        messageSender.writeObject(message);
    }

    public Message getMessage() throws IOException, ClassNotFoundException {
        return (Message) messageReceiver.readObject();
    }

    /**
     * Обрабатывает полученное на сервер сообщение от участника.
     * @param gotMessage полученное сообщение.
     */
    public void operateOn(Message gotMessage) {
        String sender = gotMessage.getSender();

        switch (gotMessage.getType()) {
            case SERVER_MSG, PRIVATE_MSG -> dispatcher.send(gotMessage);
            case TXT_MSG -> dispatcher.broadcastFrom(gotMessage);
            case REG_REQUEST -> dispatcher.changeName(sender, this);
            case LIST_REQUEST -> dispatcher.send(usersListMessage(sender));
            case EXIT_REQUEST -> dispatcher.disconnect(sender);
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

    public boolean isClosed() {
        return socket.isClosed();
    }
}
