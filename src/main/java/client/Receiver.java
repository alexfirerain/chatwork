package client;

import common.Logger;
import common.Message;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;

import static common.MessageType.SERVER_MSG;


/**
 * Приёмник, предназначенный в отдельной стопке читать из входящего потока сообщения
 * и выдавать их пользователю в консоль в правильном формате,
 * а также следить по ним за статусом зарегистрированности пользователя на сервере.
 */
public class Receiver extends Thread {
    /**
     * Клиент, запустивший этот Приёмник.
     */
    private final Client client;
    /**
     * Сокет, используемый для связи с сервером.
     */
    private final Socket connection;
    /**
     * Эфир, из которого поступают сообщения от сервера.
     */
    private final ObjectInputStream ether;
    /**
     * Логировщик, протоколирующий входящие сообщения и события, случающиеся в Приёмнике.
     */
    private final Logger logger;

    /**
     * Создаёт новый Приёмник входящих сообщений для указанного Клиента.
     * @param client клиентская программа, в которой запускается
     *               этот принимающий поток.
     * @throws IOException при ошибке получения из соединения входящего потока.
     */
    public Receiver(Client client) throws IOException {
        this.client = client;
        connection = client.getConnection();
        ether = new ObjectInputStream(connection.getInputStream());
        logger = client.logger;
//        setDaemon(true);
    }

    @Override
    public void run() {
        while (!connection.isClosed() && !interrupted()) {
            try {
                Message gotMessage = (Message) ether.readObject();
                display(gotMessage);
                checkRecipient(gotMessage);

            } catch (EOFException e) {
                String info = "Соединение c сервером завершено.";
                System.out.println(info);
                logger.logEvent(info);
//                e.printStackTrace();
                try {
                    connection.close();
                    logger.stopLogging();
                    interrupt();
//                    break;                          // ?
                } catch (IOException ex) {
                    String error = "Ошибка закрытия соединения: " + e.getMessage();
                    System.out.println(error);
                    logger.logEvent(error);
                    ex.printStackTrace();
                    break;
                }
            } catch (IOException | ClassNotFoundException e) {
                String error = "Ошибка получения сообщения: " + e.getMessage();
                System.out.println(error);
                logger.logEvent(error);
                e.printStackTrace();
                break;
            }
        }
        System.out.println("END running Receiver");     // monitor
    }

    /**
     * Проверяет, что, если это серверное сообщение, соответствует ли его поле получателя
     * тому имени, которое стоит у Клиента. Если Клиент зарегистрирован (is registered),
     * несоответствие означает, что произошла принятая сервером смена имени, — устанавливает
     * имя получателя из принятого сообщения в качестве имени в Клиенте и пересохраняет файл настроек
     * с новым именем пользователя. Если же не зарегистрирован, то соответствие означает,
     * что запрашиваемое имя принято сервером, — устанавливает флажок в Клиенте, что он отныне зарегистрирован.
     * @param messageToCheck проверяемое сообщение.
     */
    private void checkRecipient(Message messageToCheck) {

        if (messageToCheck.getType() != SERVER_MSG) return;

        String gotName = messageToCheck.getAddressee();
        boolean namesMatch = client.getUserName().equals(gotName);  // Приёмник запускается только когда userName уже != null

        if (!client.isRegistered() && namesMatch)
            client.setRegistered();

        if (client.isRegistered() && !namesMatch) {
            client.setUserName(gotName);
            client.saveSettings();
        }
    }

    /**
     * Производит отображение и логирование принятого сообщения.
     * @param gotMessage принятое сообщение.
     */
    private void display(Message gotMessage) {
        System.out.println(gotMessage);
        logger.logInbound(gotMessage);
    }
}
