package common;

import server.Server;

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
     * автор сообщения (для регистрирующего сообщения регистрируемое имя), сервер оставляет пустым
     */
    final private String sender;

    /**
     * Указатель получателя для персонального текстового (или информационного) сообщения,
     * у публичного сообщения остаётся пустым.<p>
     * Широковещательная отправка от сервера ({@code .broadcast(Message)}) явно устанавливает
     * это поле. Клиент, впервые обнаружив в этом поле в полученном сообщении имя,
     * которое собирается зарегистрировать, переходит из регистрационного режима в основной.
     */
    private String addressee;
    public void setAddressee(String addressee) {
        this.addressee = addressee;
        System.out.println("Receiver of the message is set");   // monitor
    }
    /**
     * сообщаемая в сообщении строка
     */
    final private String message;

    private Message(MessageType type, String sender, String addressee, String message) {
        this.type = type;
        this.sender = sender;
        this.addressee = addressee;
        this.message = message;
        System.out.printf("[%s]%n", this);        // monitor
    }

    @Override
    public String toString() {
        StringBuilder output = new StringBuilder(switch (type) {
            case SERVER_MSG -> ">>> Серверное сообщение:\n";
            case PRIVATE_MSG -> ">>> Личное сообщение:\n";
            default -> "";
        });
        if (sender != null)
            output.append(sender).append(" > ");
        if (message != null)
            output.append(message);
        return output.toString();
    }

    /**
     * Создаёт новое сообщение от клиента на основании его ввода.
     * @param inputText текст, введённый пользователем.
     * @param sender    имя пользователя, под которым он участвует
     *                 или планирует участвовать в беседе.
     * @return  новое сообщение с типом и получателем, определёнными
     * по условным знакам в начале введённого пользователем текста:
     * <ul>
     * <li>"@имя_получателя " = персональное сообщение</li>
     * <li>"/reg " = запрос от участника на смену имени</li>
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
        int delimiterIndex = inputText.indexOf(" ");

        if (inputText.startsWith("@")) {
            type = PRIVATE_MSG;
            addressee = inputText.substring(1, delimiterIndex);
            message = inputText.substring(delimiterIndex + 1);
        }
        if (inputText.startsWith("/")) {
            message = null;
            String command = inputText.substring(1, inputText.indexOf(" "));
            switch (command) {
                case "reg" -> {
                    type = REG_REQUEST;
                    sender = inputText.substring(delimiterIndex + 1).strip();
                    if (sender.length() > Server.nickLengthLimit)
                        sender = sender.substring(0, Server.nickLengthLimit);
                }
                case "users" -> type = LIST_REQUEST;
                case "exit" -> type = EXIT_REQUEST;
                case "terminate" -> type = SHUT_REQUEST;
                default -> message = inputText;
            }
        }
        return new Message(type, sender, addressee, message);
    }

    /**
     * Создаёт серверное сообщение с заданным текстом для указанного участника.
     * @param messageText заданный текст.
     * @param receiver    указанный участник.
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
        return Objects.hash(message);
    }
}
