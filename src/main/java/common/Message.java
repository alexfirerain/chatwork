package common;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import static common.MessageType.*;

public class Message implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * служебное поле, определяющее тип
     */
    final private MessageType type;
    /**
     * автор сообщения (для регистрирующего сообщения регистрируемое имя),
     * сервер оставляет пустым, а если это условный знак, что сессия закрывается,
     * то в нём передаётся пустая строка
     */
    final private String sender;
    /**
     * Указатель получателя для персонального текстового (или информационного) сообщения,
     * у публичного сообщения остаётся пустым.<p>
     * Сервер, отправляя сообщения серверного типа, всегда явно устанавливает это поле
     * (если имя адресата зарегистрировано). Клиент, впервые обнаружив в этом поле
     * в полученном сообщении имя, которое собирается зарегистрировать, переходит
     * из регистрационного режима в основной.
     * Обнаружив же, что адресат полученного сообщения не совпадает с ранее сохранённым,
     * Клиент понимает, что только что успешно сменил имя, и запоминает новое.
     */
    private String addressee;
    /**
     * сообщаемая в сообщении строка; у служебных сообщений пусто
     */
    final private String message;

    /**
     * Внутренний конструктор сообщения через явное указание параметров.
     * @param type      тип сообщения.
     * @param sender    отправитель сообщения.
     * @param addressee адресат сообщения.
     * @param message   текст сообщения.
     */
    private Message(MessageType type, String sender, String addressee, String message) {
        this.type = type;
        this.sender = sender;
        this.addressee = addressee;
        this.message = message;
    }
    /**
     * Устанавливает получателя и возвращает то же сообщение с изменённым полем.
     * @param addressee устанавливаемое имя получателя.
     * @return сообщение с установленным адресатом.
     */
    public Message setAddressee(String addressee) {
        this.addressee = addressee;
        return this;
    }

    /**
     * Создаёт новое серверное сообщение для указанного получателя с пустой строкой
     * в качестве отправителя (условный сигнал о закрытии соединения).
     * @param recipient получатель сообщения.
     * @param message   текст сообщения о завершении работы.
     * @return  новое стоп-сообщение с заданным текстом для адресата.
     */
    public static Message stopSign(String message, String recipient) {
        return new Message(SERVER_MSG, "", recipient, message);
    }

    @Override
    public String toString() {
        StringBuilder output = new StringBuilder(switch (type) {
            case TXT_MSG -> "";
            case SERVER_MSG -> ">>> Серверное сообщение:\n";
            case PRIVATE_MSG -> ">>> Личное сообщение:\n";
            case REG_REQUEST -> "<REG_REQUEST>\n";
            case LIST_REQUEST -> "<LIST_REQUEST>\n";
            case EXIT_REQUEST -> "<EXIT_REQUEST>\n";
            case SHUT_REQUEST -> "<SHUT_REQUEST>\n";
        });

        if (sender != null)
            output.append(sender).append(" > ");

        if (message != null)
            output.append(message);

        return output.toString();
    }

    /*
        Открытые статические методы для генерации сообщений.
     */
    /**
     * Создаёт новое сообщение от клиента на основании его ввода.
     * @param inputText текст, введённый пользователем.
     * @param sender    имя пользователя, под которым он участвует
     *                 или планирует участвовать в беседе.
     * @return  новое сообщение с типом и получателем, определёнными
     * по условным знакам в начале введённого пользователем текста:
     * <ul>
     * <li>"@имя_получателя " = персональное сообщение</li>
     * <li>"/reg новое_имя" = запрос от участника на смену имени</li>
     * <li>"/users " = запрос списка участников беседы</li>
     * <li>"/exit " = запрос на выход из беседы</li>
     * <li>"/terminate " = запрос на выключение сервера</li>
     * <li>иначе: обычное текстовое сообщение</li>
     * </ul>
     */
    public static Message fromClientInput(String inputText, String sender) {
        MessageType type = TXT_MSG;
        String addressee = null;
        String message = inputText;
        if (message.length() < 2)
            return new Message(type, sender, null, message);
        int spaceIndex = inputText.indexOf(" ");
        if (spaceIndex <= 0)
            spaceIndex = inputText.length();
        String keyword = inputText.substring(1, spaceIndex);

        if (inputText.startsWith("@")) {
            type = PRIVATE_MSG;
            addressee = keyword;
            message = spaceIndex < inputText.length() ? inputText.substring(spaceIndex + 1) : "";
        }
        if (inputText.startsWith("/")) {
            message = null;
            switch (keyword) {
                case "reg" -> {
                    type = REG_REQUEST;
                    sender = spaceIndex < inputText.length() ? inputText.substring(spaceIndex + 1).strip() : "";
                    if (sender.length() > Configurator.nickLengthLimit)
                        sender = sender.substring(0, Configurator.nickLengthLimit);
                }
                case "users" -> type = LIST_REQUEST;
                case "exit" -> type = EXIT_REQUEST;
                case "terminate" -> type = SHUT_REQUEST;
                default -> {
                    type = TXT_MSG;
                    message = inputText;
                }
            }
        }
        return new Message(type, sender, addressee, message);
    }

    /**
     * Создаёт серверное сообщение с заданным текстом для указанного участника.
     * @param messageText текст сообщения.
     * @param receiver    адресат.
     * @return  новое серверное сообщение на указанный адрес с заданным текстом.
     */
    public static Message fromServer(String messageText, String receiver) {
        return new Message(SERVER_MSG, null, receiver, messageText);
    }

    /**
     * Создаёт серверное сообщение для всех с заданным текстом.
     * @param messageText заданный текст.
     * @return  новое серверное сообщение с заданным текстом без указания получателя.
     */
    public static Message fromServer(String messageText) {
        return fromServer(messageText, null);
    }

    /**
     * Создаёт новое сообщение с запросом регистрации без необходимости введения
     * условного знака "/reg" (в ответ на запрос имени при вхождении в беседу).
     * @param putName введённый пользователем текст, трактуемый как регистрируемое имя.
     * @return  новое сообщение с запросом регистрации введённого пользователем имени.
     */
    public static Message registering(String putName) {
        if (putName.startsWith("/reg "))
            putName = putName.substring("/reg ".length()).strip();
        return new Message(REG_REQUEST, putName, null, null);
    }
    /**
     * Сообщает, является ли указанная строка существующей и соответствующей требованиям к регистрируемому имени.
     * @param name строка.
     * @return  {@code истинно}, если строка удовлетворяет регулярке, то есть имеет хотя бы одну букву,
     * а также любое количество цифр или пробелов.
     */
    public static boolean isAcceptableName(String name) {
        return name != null && name.matches("[\\p{L}]+\\d*\\s*");
    }

    /*
        Информационные методы сообщения.
     */
    /**
     * Сообщает, является ли сообщение стоп-сигналом.
     * @return {@code истинно}, если {@code "".equals(sender)}.
     */
    public boolean isStopSign() {
        return "".equals(getSender());
    }
    /**
     * Сообщает, является ли сообщение серверным.
     * @return {@code истинно}, если типа {@code SERVER_MSG};
     */
    public boolean isServerMessage() { return getType() == SERVER_MSG; }
    /**
     * Сообщает, является ли сообщение запросом.
     * @return {@code истинно}, если не является ни серверным, ни переправляемым;
     */
    public boolean isRequest() { return getType().ordinal() > 2; }
    /**
     * Сообщает, является ли сообщение переправляемым.
     * @return {@code истинно}, если типа {@code TXT_MSG || PRIVATE_MSG};
     */
    public boolean isTransferable() { return getType() == TXT_MSG || getType() == PRIVATE_MSG; }

    public MessageType getType() {
        return type;
    }
    public String getSender() {
        return sender;
    }
    public String getAddressee() {
        return addressee;
    }
    public String getMessage() {
        return message;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message another = (Message) o;
        return type == another.type &&
                Objects.equals(sender, another.sender) &&
                Objects.equals(addressee, another.addressee) &&
                Objects.equals(message, another.message);
    }
    @Override
    public int hashCode() {
        return Objects.hash(sender, message);
    }
}
