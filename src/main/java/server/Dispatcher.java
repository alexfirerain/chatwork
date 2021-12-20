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

import static server.TextConstants.*;

/**
 * Реализует логику работы с подключениями: регистрация участников беседы, их учёт,
 * определение, кому какое сообщение отправлять, обработка событий смены имени,
 * выхода из разговора или команды на остановку сервера.
 */
public class Dispatcher {

    /**
     * Сервер, работающий с Диспетчером.
     */
    private final Server host;
    /**
     * Реестр зарегистрированных участников беседы в виде карты "имя-соединение".
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
        this.host = host;
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
            logger.logEvent(REGISTRATION_REJECTED.formatted(userName, connection));
            return false;
        }
        users.put(userName, connection);
        logger.logEvent(REGISTRATION_SUCCESS.formatted(userName, connection));
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
        return users.entrySet().stream()
                .filter(entry -> entry.getValue() == connection)
                .findFirst().map(Map.Entry::getKey)
                .orElse(null);
    }


    /*
        Методы отсылки или рассылки сообщений участникам.
     */
    /**
     * Отсылает данное сообщение участнику с данным именем.
     * Поле адресата оставляет неизменным.
     * Логирует только события, но не сообщения.
     * @param message  данное сообщение.
     * @param username данное имя участника.
     */
    private void send(Message message, String username) {
        Connection channel = users.get(username);
        String error = null;
        if (channel != null) {
            try {
                channel.sendMessage(message);
            } catch (SocketException e) {
                error = "Соединение с участником %s не доступно: %s".formatted(username, e.getMessage());
                e.printStackTrace();
                goodbyeUser(username);
            } catch (IOException e) {
                error = "Сообщение участнику %s не отправилось: %s".formatted(username, e.getMessage());
                e.printStackTrace();
            }
        } else {
            error = "Сообщение не может быть отправлено: участник %s не подключён.".formatted(username);
        }
        if (error != null) {
            System.out.println(error);
            logger.logEvent(error);
        }

    }

    /**
     * Посылает сообщение указанному в нём адресату и заносит его в лог как отправленное.
     * Если адресат не указан, игнорирует сообщение.
     * @param msg посылаемое сообщение.
     */
    private void send(Message msg) {
        send(msg, true);
    }

    /**
     * Посылает сообщение указанному в нём адресату.
     * Если адресат не указан, игнорирует сообщение.
     * Если указано, логирует сообщение как исходящее.
     * @param msg   отсылаемое и логируемое сообщение.
     * @param toLog нужно ли занести отправку в лог.
     */
    private void send(Message msg, boolean toLog) {
        String addressee = msg.getAddressee();
        if (addressee != null) {
            send(msg, addressee);
            if (toLog)
                logger.logOutbound(msg);
        }
    }

    /**
     * Если сообщение публичное, рассылает его всем актуальным участникам, кроме его отправителя.
     * Если сообщение частное, отправляет его адресату.
     * Логирует сообщение как пересланное.
     * @param message транслируемое сообщение.
     */
    private void forward(Message message) {
        if (!message.isTransferrable()) return;
        logger.logTransferred(message);
        if (message.getAddressee() == null)
            getUsersBut(message.getSender()).forEach(user -> send(message, user));
        else
            send(message, false);
    }

    /**
     * Отсылает данное (серверное) сообщение всем актуальным участникам,
     * при этом явно добавляя в него указание получателя.
     * Логирует сообщение как одну общую рассылку.
     * @param message данное сообщение.
     */
    private void broadcast(Message message) {
        logger.logOutbound(message);
        getUsers().forEach(user -> send(message.setAddressee(user), false));
    }

    /**
     * Отсылает всем, кроме одного специфицированного, участникам одно сообщение (логируя его как общее,
     * но явно проставляя получателей), а специфицированному участнику – другое сообщение.
     * @param generalMessage сообщение, которое отсылается всем, кроме одного.
     * @param exclusiveOne   имя пользователя, получающего эксклюзивное сообщение.
     * @param specialMessage специальное сообщение для специфицированного получателя.
     */
    private void castWithExclusive(Message generalMessage, String exclusiveOne, Message specialMessage) {
        logger.logOutbound(generalMessage);
        getUsersBut(exclusiveOne).forEach(user -> send(generalMessage.setAddressee(user), false));
        send(specialMessage.setAddressee(exclusiveOne));
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
            case TXT_MSG, PRIVATE_MSG -> forward(gotMessage);
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
    private void changeName(String newName, Connection connection) {
        String oldName = getUserForConnection(connection);
        if (addUser(newName, connection)) {
            users.remove(oldName);
            broadcast(Message.fromServer(CHANGE_SUCCESS.formatted(oldName, newName)));
        } else {
            send(Message.fromServer(CHANGE_FAILED.formatted(newName), oldName));
        }
    }

    /**
     * Уведомляет всех подключённых участников о подключении нового.
     * А новоподключённому высылает привет и инструкцию.
     * @param greeted новозарегистрированное имя.
     */
    public void greetUser(String greeted) {
        castWithExclusive(Message.fromServer(ENTER_USER.formatted(greeted)), greeted,
                Message.fromServer(welcomeText(greeted)));
    }

    /**
     * Отключает указанного участника от беседы: закрывает ассоциированное
     * с ним соединение, удаляет его из реестра и уведомляет актуальных участников о его уходе.
     * @param username имя участника, покидающего чат.
     */
    public void goodbyeUser(String username) {
        if (disconnect(username, CONNECTION_CLOSING))
            broadcast(Message.fromServer(USER_LEAVING.formatted(username)));
    }

    /**
     * Отключает указанного участника от беседы: закрывает ассоциированное с ним соединение
     * и удаляет его из реестра участников.
     * @param username имя участника, покидающего чат.
     * @param farewell текст прощального сообщения отключаемому.
     * @return {@code истинно}, если такой участник был найден и теперь отключён.
     */
    private boolean disconnect(String username, String farewell) {
        try {
            send(Message.stopSign(farewell, username));
            getConnectionForUser(username).close();
            users.remove(username);
            return true;
        } catch (Exception e) {
            String error = DISCONNECT_FAILED.formatted(username);
            System.out.println(error);
            logger.logEvent(error);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Рассылает всем участникам уведомление о завершении работы
     * и отключает их всех.
     */
    public void closeSession() {
        getUsers().forEach(username -> disconnect(username, CLOSING_TXT));
    }

    /**
     * Отсылает и логирует серверное сообщение со сведениями о подключённых
     * в текущий момент участниках тому, кто запросил этот список.
     * @param requesting участник, запросивший список.
     */
    private void sendUserList(String requesting) {
        send(Message.fromServer(getUserListing(), requesting));
    }


    /*
        Генераторы текста.
     */

    /**
     * Выдаёт текстовой блок для приветствия новоподключённого, в котором сообщает адрес сервера,
     * список подключённых к комнате и перечень доступных команд (кроме команды остановки сервера).
     * @param greeted новоподкючённый.
     * @return  форматированный текстовой блок-приветствие.
     */
    private String welcomeText(String greeted) {
        return WELCOME_TEXT.formatted(greeted, host.HOST, host.PORT, getUserListing());
    }
    /**
     * Выдаёт текстовое представление реестра зарегистрированных пользователей.
     * @return текстовой блок о количестве участников и их именах.
     */
    private String getUserListing() {
        return getUsers().stream()
                .collect(Collectors.joining(
                        "\n", "Подключено участников: %d:\n".formatted(getUsers().size()), ""));
    }




}



