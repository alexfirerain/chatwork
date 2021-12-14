package server;

import common.Logger;
import common.Message;

import java.io.IOException;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Реализует логику работы с подключениями: регистрация участников беседы, их учёт,
 * определение, кому какое сообщение отправлять, обработка событий смены имени,
 * выхода из разговора или команды на остановку сервера.
 */
public class Dispatcher {
    private static final String CHANGE_FAILED = "Сменить имя на такое не получилось!";
    private static final String CLOSING_TXT = "Сервер завершает работу!";

    /**
     * Реестр участников в виде карты "имя-соединение".
     */
    private final Map<String, Connection> users;
    /**
     * Логировщик Сервера, протоколирующий события в этом Диспетчере.
     */
    private final Logger logger;

    /**
     * Инициализирует новый Диспетчер с пустым списком участников.
     * @param host Сервер, обслуживаемый Диспетчером.
     */
    public Dispatcher(Server host) {
        users = new ConcurrentHashMap<>();
        logger = host.logger;
    }


    /*
        Методы работы с реестром участников.
     */
    /**
     * Фиксирует в реестре связь данного имени с данным соединением, если имя и соединение существуют,
     * и такое имя на текущий момент не зафиксировано в списке актуальных.
     * @param userName   регистрируемое имя.
     * @param connection регистрируемое соединение.
     * @return  {@code ложно}, если предлагаемое имя уже зарегистрировано или не является допустимым
     * или недоступно предлагаемое соединение; иначе  {@code истинно} (то есть был ли добавлен элемент в реестр).
     */
    public boolean addUser(String userName, Connection connection) {
        if (!Message.isAcceptableName(userName)
                || connection == null || connection.isClosed()
                || users.containsKey(userName)) {
            logger.logEvent("Отказ в регистрации имени " + userName + " для " + connection);
            return false;
        }
        users.put(userName, connection);
        logger.logEvent("Имя " + userName + " зарегистрировано для " + connection);
        return true;
    }

    /**
     * Возвращает набор всех актуальных пользователей.
     * @return набор подключённых в настоящий момент участников.
     */
    public Set<String> getUsers() {
        return new HashSet<>(users.keySet());
    }

    /**
     * Возвращает набор актуальных участников за исключением одного указанного.
     * @param aUser  участник, которого не нужно упоминать.
     * @return набор актуальных участников кроме одного.
     */
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
    public Connection getConnectionForUser(String user) {
        return users.get(user);
    }

    /**
     * Сообщает зарегистрированное на данное соединение имя участника.
     * @param connection данное соединение.
     * @return  имя участника, на которого зарегистрировано это соединение,
     * или, если такового нет, {@code ничто}.
     */
    public String getUserForConnection(Connection connection) {
        for (Map.Entry<String, Connection> entry : users.entrySet())
            if (entry.getValue() == connection)
                return entry.getKey();
        return null;
    }


    /*
        Методы отсылки или рассылки сообщений участникам.
     */
    /**
     * Отсылает данное сообщение участнику с данным именем.
     * @param message  данное сообщение.
     * @param username данное имя участника.
     */
    public void sendTo(Message message, String username) {
        Connection channel = users.get(username);
        String error = null;
        if (channel != null) {
            try {
                channel.sendMessage(message);
            } catch (SocketException e) {
                error = String.format(
                        "Соединение с участником %s не доступно: %s", username, e.getMessage());
                e.printStackTrace();
                goodbyeUser(username);
            } catch (IOException e) {
                error = String.format(
                        "Сообщение участнику %s не отправилось: %s", username, e.getMessage());
                e.printStackTrace();
            }
        } else {
            error = String.format(
                    "Сообщение не может быть отправлено: участник %s не подключён.", username);
        }
        if (error != null) {
            System.out.println(error);
            logger.logEvent(error);
        }

    }

    /**
     * Отсылает данное сообщение всем актуальным участникам,
     * при этом явно добавляя в это сообщение указание получателя.
     * @param message данное сообщение.
     */
    public void broadcast(Message message) {
        logger.logOutbound(message);
        getUsers().forEach(user -> {
            message.setAddressee(user);
            sendTo(message, user);
        });
    }

    /**
     * Посылает сообщение указанному в нём адресату, либо, если адресат не указан,
     * всем актуальным участникам (явно прописав получателей).
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
    public void forward(Message message) {
        getUsersBut(message.getSender())
                .forEach(user -> sendTo(message, user));
    }

    /*
        Методы обработки специальных случаев взаимодействия с клиентом.
     */
    /**
     * Селектор действия в ответ на получение нового сообщения.
     * @param gotMessage полученное сообщение.
     * @param source     соединение, с которого пришло это сообщение.
     */
    public void operateOn(Message gotMessage, Connection source) {
        String sender = gotMessage.getSender();
        switch (gotMessage.getType()) {
            case TXT_MSG -> forward(gotMessage);
            case PRIVATE_MSG -> send(gotMessage);
            case LIST_REQUEST -> sendUserList(sender);
            case REG_REQUEST -> changeName(sender, source);
            case EXIT_REQUEST -> goodbyeUser(sender);
            case SHUT_REQUEST -> source.getShut();
        }
    }

    /**
     * Меняет, если это возможно, регистрированное имя для соединения,
     * с которого пришёл такой запрос. Если по какой-то причине это не получается,
     * шлёт запросившему об этом уведомление.
     * @param newName    имя, под которым хочет перерегистрироваться зарегистрированный пользователь.
     * @param connection соединение, которое требуется переназначить на новое имя.
     */
    public void changeName(String newName, Connection connection) {     // TODO: поскольку блокирующее, перенести в Соединение!
        String oldName = getUserForConnection(connection);
        if (addUser(newName, connection)) {
            users.remove(oldName);
            broadcast(Message.fromServer(nameChanged(oldName, newName)));
        } else {
            send(Message.fromServer(CHANGE_FAILED, oldName));
        }
    }

    /**
     * Отключает указанного участника от беседы: закрывает ассоциированное
     * с ним соединение, удаляет его из реестра и уведомляет актуальных участников о его уходе.
     * @param username имя участника, покидающего чат.
     */
    public void goodbyeUser(String username) {
        if (disconnect(username))
            broadcast(Message.fromServer(farewell(username)));
    }

    /**
     * Отключает указанного участника от беседы: закрывает ассоциированное с ним соединение
     * и удаляет его из реестра участников.
     * @param username имя участника, покидающего чат.
     * @return {@code истинно}, если такой участник был найден и теперь отключён.
     */
    private boolean disconnect(String username) {
        try {
            send(Message.stopSign(username, "Соединение закрывается. Пока!"));
            getConnectionForUser(username).close();
            users.remove(username);
            return true;
        } catch (Exception e) {
            String error = "Не удалось отключить участника: " + username;
            System.out.println(error);
            logger.logEvent(error);
            e.printStackTrace();
            return false;
        }
    }

    @Deprecated
    private void keepAlive(String clientName) {
        send(Message.onlineSign(clientName));
    }

    /**
     * Рассылает всем участникам уведомление о завершении работы
     * и отключает их всех.
     */
    public void closeSession() {
        broadcast(Message.stopSign(CLOSING_TXT));
        getUsers().forEach(this::disconnect);
    }

    public void greetUser(String greeted) {
        broadcast(Message.fromServer(greeting(greeted)));
    }

    /**
     * Отсылает серверное сообщение со сведениями о подключённых
     * в текущий момент участниках тому, кто запросил этот список.
     * @param requesting участник, запросивший список.
     */
    public void sendUserList(String requesting) {
        String report = getUsers().stream().map(user -> "\n" + user)
                .collect(Collectors.joining("", "Подключено участников: " + getUsers().size() + ":", ""));
        send(Message.fromServer(report, requesting));
    }

    /*
        Методы-генераторы текста серверных уведомлений.
     */
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
}
