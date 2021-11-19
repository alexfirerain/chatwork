package server;

import connection.Connection;
import connection.Message;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final File settingsSource = new File("settings.txt");
    public static final int nickLengthLimit = 15;
    private static final String PROMPT_TEXT = "Добро пожаловать в переговорную комнату!\n" +
            "Введите своё имя (до " + nickLengthLimit + " букв)";
    private static final String WARN_TXT = "Такое имя занято уже!";

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
            case REG_REQUEST -> {
                if (registerUser(sender))
                    users.sendToAll(userComeMessage(sender));
            }
//            case LIST_REQUEST -> {
//                users.sendTo(userlistMessage(), sender);
//            }
//            case EXIT_REQUEST -> {
//                disconnect(sender);
//                users.sendToAll(userGoneMessage(sender));
//            }
//            case SHUT_REQUEST -> {
//                getShut(sender);
//            }
        }
    }

    private boolean registerUser(String sender) {

        return false;
    }

    public boolean hasMappingFor(Connection connection) {
        return users.hasMappingFor(connection);
    }

    public void registerUser(Connection connection) {
        try {
            connection.send(promptMessage());
            String sender = connection.getMessage().getSender();
            while(users.contains(sender)) {
                connection.send(warnMessage());
                sender = connection.getMessage().getSender();
            }
            users.addUser(sender, connection);
            users.sendToAll(userComeMessage(sender));
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }


    }

    private Message userComeMessage(String entrant) {
        return Message.fromServer(greeting(entrant));
    }

    private String greeting(String entrant) {
        return "К беседе присоединился %s!"
                .formatted(entrant);
    }

    private Message warnMessage() {
        return Message.fromServer(WARN_TXT);
    }

    private Message promptMessage() {
        return Message.fromServer(PROMPT_TEXT);
    }
}
