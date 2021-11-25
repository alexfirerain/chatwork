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

    public Receiver(Client client, ObjectInputStream ether) {
        this.client = client;
        this.ether = ether;
    }

    @Override
    public void run() {
        while (!client.getSocket().isClosed() && !interrupted()) {
            try {
                Message gotMessage = (Message) ether.readObject();
                display(gotMessage);
                if (Objects.equals(gotMessage.getAddressee(), client.getUserName()))
                    client.setRegistered();




            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void display(Message gotMessage) {

        System.out.println(gotMessage);
    }
}
