package client;

import common.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Objects;


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
        System.out.println("Receiver started");
        while (!client.getConnection().isClosed() && !interrupted()) {
            try {
                Message gotMessage = (Message) ether.readObject();
                display(gotMessage);

                String currentName = client.getUserName();
                String gotName = gotMessage.getAddressee();

                System.out.println(client + "'s name is " + currentName);      // monitor
                System.out.println("the message is for " + gotName);      // monitor

                if (!client.isRegistered() && currentName.equals(gotName))   // Приёмник запускается только когда userName уже != null
                    client.setRegistered();

                if (client.isRegistered() && !currentName.equals(gotName)) {
                    client.setUserName(gotName);
                    client.saveSettings();
                }

            } catch (IOException | ClassNotFoundException e) {
                String error = "ошибка получения сообщения: " + e.getMessage();
                System.out.println(error);
                e.printStackTrace();
//                break;
            }
        }
    }

    private void display(Message gotMessage) {

        System.out.println(gotMessage);
    }
}
