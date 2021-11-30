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

                if (!client.isRegistered() && client.getUserName().equals(gotMessage.getAddressee()))   // Приёмник запускается только когда userName уже != null
                    client.setRegistered();

                if (client.isRegistered() && !client.getUserName().equals(gotMessage.getAddressee()))
                    client.setUserName(gotMessage.getAddressee());

            } catch (IOException | ClassNotFoundException e) {
                String error = "getting message error: " + e.getMessage();
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
