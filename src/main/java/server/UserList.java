package server;

import connection.Connection;
import connection.Message;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class UserList {
    private final Map<String, Connection> users;

    public UserList() {
        users = new ConcurrentHashMap<>();
    }

    public boolean contains(String user) {
        return users.containsKey(user);
    }

    public Set<String> getUsers() {
        return new HashSet<>(users.keySet());
    }

    public boolean addUser(String userName, Connection connection) {
        return users.put(userName, connection) == null;
    }

    public boolean removeUser(String userName) {
        return users.remove(userName) != null;
    }

    public Set<String> getUsersBut(String aUser) {
        return getUsers().stream()
                .filter(x -> !x.equals(aUser))
                .collect(Collectors.toSet());
    }

    public Connection getConnectionFor(String user) {
        return users.get(user);
    }

    public boolean hasMappingFor(Connection connection) {
        return users.containsValue(connection);
    }

    public void sendTo(Message message, String username) {
        Connection channel = users.get(username);
        if (channel != null) {
            try {
                channel.send(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendToAll(Message message) {
        getUsers().forEach(user -> sendTo(message, user));
    }

    public void sendToAllBut(Message message, String username) {
        getUsersBut(username).forEach(user -> sendTo(message, user));
    }
}
