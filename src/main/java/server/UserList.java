package server;

import connection.Connection;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class UserList {
    private final Map<String, Connection> users;

    public UserList() {
        users = new ConcurrentHashMap<>();
    }

    public boolean contains(String user) {
        return users.containsKey(user);
    }

    public Set<String> getUsers() {
        return users.keySet();
    }

    public boolean addUser(String userName, Connection connection) {
        return users.put(userName, connection) == null;
    }

    public boolean removeUser(String userName) {
        return users.remove(userName) != null;
    }

    public Set<String> getUsersBut(String aUser) {
        Set<String> allUsers = users.keySet();
        allUsers.remove(aUser);
        return allUsers;
    }

    public Connection getConnectionFor(String user) {
        return users.get(user);
    }
}
