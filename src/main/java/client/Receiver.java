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
        System.out.println("Receiver started");
        while (!client.getSocket().isClosed() && !interrupted()) {
            try (ether) {
                Message gotMessage = (Message) ether.readObject();
                display(gotMessage);

                if (!client.isRegistered() &&
                        Objects.equals(
                                gotMessage.getAddressee(), client.getUserName()))
                    client.setRegistered();

            } catch (IOException | ClassNotFoundException e) {
                String error = "getting message error: " + e.getMessage();
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
