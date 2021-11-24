package client;

import common.Message;

import java.io.IOException;
import java.io.ObjectInputStream;


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
                if (gotMessage.getAddressee() != null)
                    client.setRegistered();




            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void display(Message gotMessage) {
        StringBuilder output = new StringBuilder(switch (gotMessage.getType()) {
            case SERVER_MSG -> ">>> Серверное сообщение:\n";
            case PRIVATE_MSG -> ">>> Личное сообщение:\n";
            default -> "";
        });

        if (gotMessage.getSender() != null)
            output.append(gotMessage.getSender()).append(" > ");

        output.append(gotMessage.getMessage());

        String text = output.toString();

        System.out.println(text);
    }
}
