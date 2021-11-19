package server;

import connection.Connection;
import connection.Message;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final File settingsSource = new File("settings.txt");
    public static final int nickLengthLimit = 15;

    private final int serverPort;
    private final byte[] password;
    private final ExecutorService connections;
    private final UserList users = new UserList();

    public Server() {
        serverPort = 7777;
        password = "0000".getBytes();
        connections = Executors.newCachedThreadPool();
    }


    public void operateOn(Message gotMessage) {
        String sender = gotMessage.getSender();
        String addressee = gotMessage.getAddressee();
        
        switch (gotMessage.getType()) {

            case SERVER_MSG -> {
                if (addressee != null) users.sendTo(gotMessage, addressee);
                else users.sendToAll(gotMessage);
            }
            case TXT_MSG -> {
                users.sendToAllBut(gotMessage, sender);
            }
            case PRIVATE_MSG -> {
                users.sendTo(gotMessage, addressee);
            }
//            case REG_REQUEST -> {
//                registerUser(sender);
//                users.sendToAll(userComeMessage());
//            }
//            case LIST_REQUEST -> {
//                users.sendTo(userlistMessage(), sender);
//            }
//            case EXIT_REQUEST -> {
//                disconnect(sender);
//                users.sendToAll(userGoneMessage());
//            }
//            case SHUT_REQUEST -> {
//                getShut(sender);
//            }
        }
    }

    public boolean hasMappingFor(Connection connection) {
        return users.hasMappingFor(connection);
    }
}
