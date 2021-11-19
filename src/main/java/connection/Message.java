package connection;

import server.Server;

import java.util.Objects;

import static connection.MessageType.*;

public class Message {
    /**
     * служебное поле, определяющее тип
     */
    final private MessageType type;
    /**
     * автор сообщения (для регистрирующего сообщения регистрируемое имя), сервер может оставлять пустым
     */
    final private String sender;
    /**
     * указатель получателя для персонального текстового (или информационного) сообщения, у публичного сообщения остаётся пустым
     */
    final private String addressee;
    /**
     * сообщаемая в сообщении строка
     */
    final private String message;

    private Message(MessageType type, String sender, String addressee, String message) {
        this.type = type;
        this.sender = sender;
        this.addressee = addressee;
        this.message = message;
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
            String command = inputText.substring(1, inputText.indexOf(" "));
            message = null;
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

    public static Message fromServer(String messageText, String receiver) {
        return new Message(SERVER_MSG, null, receiver, messageText);
    }
    
}
