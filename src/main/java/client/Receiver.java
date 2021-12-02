package client;

import common.Message;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;

import static common.MessageType.SERVER_MSG;


/**
 * Нить, предназначенная читать из входящего потока сообщения
 * и выдавать их пользователю в консоль в правильном формате.
 */
public class Receiver extends Thread {
    private final Client client;
    private final ObjectInputStream ether;

    /**
     * Создаёт новый Приёмник входящих сообщений для указанного Клиента.
     * @param client клиентская программа, в которой запускается
     *               этот принимающий поток.
     * @throws IOException при ошибке получения из соединения входящего потока.
     */
    public Receiver(Client client) throws IOException {
        this.client = client;
        ether = new ObjectInputStream(client.getConnection().getInputStream());
        setDaemon(true);
    }

    @Override
    public void run() {
//        System.out.println("Receiver started");             // monitor
        while (!client.getConnection().isClosed() && !interrupted()) {
            try {
                Message gotMessage = (Message) ether.readObject();
                display(gotMessage);


                if (gotMessage.getType() == SERVER_MSG) {            // по адресату широковещательных сообщений судим о регистрированности
                    String gotName = gotMessage.getAddressee();
                    boolean namesMatch = client.getUserName().equals(gotName);

//                    System.out.println(client + "'s name is " + currentName);      // monitor
//                    System.out.println("the message is for " + (gotName == null ? "everybody" : gotName));      // monitor

                    if (!client.isRegistered() && namesMatch)   // Приёмник запускается только когда userName уже != null
                        client.setRegistered();

                    if (client.isRegistered() && !namesMatch) {
                        client.setUserName(gotName);
                        client.saveSettings();
                    }
                }

            } catch (EOFException e) {
                String info = "Соединение c сервером завершено.";
                System.out.println(info);
//                e.printStackTrace();
                try {
                    client.getConnection().close();
                    break;
                } catch (IOException ex) {
                    String error = "Ошибка закрытия соединения: " + e.getMessage();
                    System.out.println(error);
                    ex.printStackTrace();
                }
            } catch (IOException | ClassNotFoundException e) {
                String error = "Ошибка получения сообщения: " + e.getMessage();
                System.out.println(error);
                e.printStackTrace();
                break;
            }
        }
    }

    private void display(Message gotMessage) {
        System.out.println(gotMessage);
    }
}
