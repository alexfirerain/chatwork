package client;

import java.io.ObjectInputStream;

/**
 * Нить, предназначенная читать из входящего потока сообщения
 * и выдавать их участнику в консоль в правильном формате.
 */
public class Receiver extends Thread {
    private ObjectInputStream ether;
}
