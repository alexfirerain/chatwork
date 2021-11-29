package primitive;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class PClient {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Socket connection = new Socket("localhost", 7777);
        System.out.println("connected up to " + connection.getRemoteSocketAddress() + " @" + connection.getLocalPort());

        ObjectInputStream in = new ObjectInputStream(connection.getInputStream());
        ObjectOutputStream out = new ObjectOutputStream(connection.getOutputStream());
        Scanner input = new Scanner(System.in);

        System.out.println("streams got");

        System.out.println(in.readObject());
        out.writeObject(PMessage.hello());
        out.flush();

        while (!connection.isClosed()) {
            out.writeObject(new PMessage(input.nextLine()));
            out.flush();

            System.out.println(in.readObject());

        }



    }
}
