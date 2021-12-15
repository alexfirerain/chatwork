package server;

public class TextConstants {
    static final String PROMPT_TEXT = ("""
            Добро пожаловать в переговорную комнату @%s:%d!
            Пишите в беседу свои сообщения и читайте сообщения других участников.
            Доступные команды:
                /reg <имя>      = зарегистрироваться под именем
                /users          = получить список подключённых к переговорной
                @<имя> <текст>  = личное сообщение собеседнику
                /exit           = выйти из комнаты
            %s""");

    public static final String ENTER_USER = "К беседе присоединяется %s!";
    public static final String CLOSING_TXT = "Сервер завершает работу!";
    public static final String PASSWORD_REQUEST = "Введите пароль для управления сервером";


    public static final String REGISTRATION_SUCCESS = "Имя %s зарегистрировано для %s";
    public static final String REGISTRATION_WARNING = "Зарегистрировать имя %s не получилось, попробуйте другое!";
    public static final String REGISTRATION_REJECTED = "Отказ в регистрации имени %s для %s";
    public static final String CHANGE_SUCCESS = "%s меняет имя на %s!";
    public static final String CHANGE_FAILED = "Сменить имя на %s не получилось!";


    public static final String USER_LEAVING = "%s оставляет беседу.";
    public static final String CONNECTION_CLOSING = "Соединение закрывается. Пока!";
    public static final String DISCONNECT_FAILED = "Не удалось отключить участника: %s";
}
