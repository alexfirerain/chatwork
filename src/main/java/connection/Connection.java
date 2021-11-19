package connection;

import server.Server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class Connection implements Runnable, AutoCloseable {
    private final Server host;
    private final Socket socket;
    private final ObjectInputStream messageReceiver;
    private final ObjectOutputStream messageSender;

    public Connection(Server host, Socket socket) throws IOException {
        this.host = host;
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
        // пока не зарегистрируется участник, регистрируем
        while (!host.hasMappingFor(this)) {

        }

        // пока соединено, считываем входящие сообщения и передаём их серверу на обработку.
        while (!socket.isClosed()) {
            try {
                Message gotMessage = (Message) messageReceiver.readObject();
                host.operateOn(gotMessage);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public void send(Message message) throws IOException {
        messageSender.writeObject(message);
    }
}
