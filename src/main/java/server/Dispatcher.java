package server;

import connection.Message;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Dispatcher {
    private static final String PROMPT_TEXT = "Добро пожаловать в переговорную комнату!\n" +
            "Введите своё имя (до " + Server.nickLengthLimit + " букв)";
    private static final String WARN_TXT = "Зарегистрировать такое имя не получилось!";
    private static final String CHANGE_FAILED = "Сменить имя на такое не получилось!";

    private final Map<String, Connection> users;

    public Dispatcher() {
        users = new ConcurrentHashMap<>();
    }

    public boolean contains(String user) {
        return users.containsKey(user);
    }

    public Set<String> getUsers() {
        return new HashSet<>(users.keySet());
    }

    /**
     * Фиксирует связь данного имени с данным соединением, если имя и соединение существуют
     * и такое имя на текущий момент не зафиксировано в списке актуальных.
     * @param userName   регистрируемое имя.
     * @param connection регистрируемое соединение.
     * @return  {@code ложно}, если предлагаемое имя уже зарегистрировано или предлагаются
     * пустые или никакие имя или соединение; иначе  {@code истинно}.
     */
    public boolean addUser(String userName, Connection connection) {
        if (userName == null || userName.isBlank()
                || connection == null || connection.isClosed()
                || users.containsKey(userName))
            return false;

        users.put(userName, connection);
        return true;
    }

    public boolean removeUser(String userName) {
        return users.remove(userName) != null;
    }

    public Set<String> getUsersBut(String aUser) {
        return getUsers().stream()
                .filter(x -> !x.equals(aUser))
                .collect(Collectors.toSet());
    }

    /**
     * Выдаёт соединение, ассоциированное с указанным именем участника.
     * @param user имя, для которого ищется соединение.
     * @return соединение, ассоциированное с участником, либо,
     * если такое имя не найдено, {@code ничто}.
     */
    public Connection getConnectionFor(String user) {
        return users.get(user);
    }

    /**
     * Сообщает, связано ли данное соединение с именем участника.
     * @param connection данное соединение.
     * @return  {@code истинно}, если такая связь имеет место.
     */
    public boolean hasMappingFor(Connection connection) {
        return users.containsValue(connection);
    }

    /**
     * Отсылает данное сообщение участнику с данным именем.
     * @param message  данное сообщение.
     * @param username данное имя участника.
     */
    public void sendTo(Message message, String username) {
        Connection channel = users.get(username);
        if (channel != null) {
            try {
                channel.send(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            throw new IllegalArgumentException(
                    "Сообщение не может быть отправлено: участник %s не подключён."
                    .formatted(username));
        }
    }

    /**
     * Отсылает данное сообщение всем актуальным участникам.
     * @param message данное сообщение.
     */
    public void broadcast(Message message) {
        getUsers().forEach(user -> sendTo(message, user));
    }

    /**
     * Отсылает данное сообщение всем актуальным участникам кроме указанного.
     * @param message  данное сообщение.
     * @param username имя участника, которому отсылать не надо.
     */
    public void sendToAllBut(Message message, String username) {
        getUsersBut(username).forEach(user -> sendTo(message, user));
    }

    /**
     * Проводит регистрацию имени пользователя для данного соединения.
     * @param connection соединение, используемое для общения с пользователем
     *                   и привязываемое к полученному от него имени.
     */
    public void registerUser(Connection connection) {
        try {
            connection.send(Message.fromServer(PROMPT_TEXT));
            String sender = connection.getMessage().getSender();
            while(!addUser(sender, connection)) {
                connection.send(Message.fromServer(WARN_TXT));
                sender = connection.getMessage().getSender();
            }
            broadcast(Message.fromServer(greeting(sender)));
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Меняет, если это возможно, регистрированное имя для соединения,
     * с которого пришёл такой запрос. Если по какой-то причине это не получается,
     * шлёт запросившему об этом уведомление.
     * @param newName    имя, под которым хочет перерегистрироваться зарегистрированный пользователь.
     * @param connection соединение, которое требуется переназначить на новое имя.
     */
    public void changeName(String newName, Connection connection) {
        String oldName = getUserForConnection(connection);
        if (addUser(newName, connection)) {
            users.remove(oldName);
            broadcast(Message.fromServer(nameChanged(oldName, newName)));
        } else {
            try {
                connection.send(Message.fromServer(CHANGE_FAILED, oldName));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Сообщает зарегистрированное на данное соединение имя участника.
     * @param connection данное соединение.
     * @return  имя участника, на которого зарегистрировано это соединение,
     * или, если такового нет, {@code ничто}.
     */
    private String getUserForConnection(Connection connection) {
        for (Map.Entry<String, Connection> entry : users.entrySet())
            if (entry.getValue() == connection)
                return entry.getKey();
        return null;
    }

    /**
     * Посылает сообщение указанному в нём адресату, либо,
     * если адресат не указан, всем актуальным участникам.
     * @param msg посылаемое сообщение.
     */
    public void send(Message msg) {
        String addressee = msg.getAddressee();
        if (addressee == null)
            broadcast(msg);
        else
            sendTo(msg, addressee);
    }

    /**
     * Пересылает сообщение всем актуальным участникам, кроме пославшего это сообщение.
     * @param message рассылаемое сообщение.
     */
    public void broadcastFrom(Message message) {
        sendToAllBut(message, message.getSender());
    }

    /**
     * Отключает указанного участника от беседы: закрывает ассоциированное
     * с ним соединение, удаляет его из реестра и уведомляет актуальных участников о его уходе.
     * @param username имя участника, покидающего чат.
     */
    public void disconnect(String username) {
        try {
            getConnectionFor(username).close();
            users.remove(username);
            broadcast(Message.fromServer(farewell(username)));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private String greeting(String entrant) {
        return "К беседе присоединяется %s!"
                .formatted(entrant);
    }
    private String nameChanged(String oldName, String newName) {
        return "%s меняет имя на %s!"
                .formatted(oldName, newName);
    }
    private String farewell(String leavingUser) {
        return "%s оставляет беседу."
                .formatted(leavingUser);
    }

    public void getShut(String requesting, Server server) {

    }
}
