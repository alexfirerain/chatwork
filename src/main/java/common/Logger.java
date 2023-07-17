package common;

import java.io.File;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.IOException;

/**
 * Логировщик предоставляет функционал для протоколирования определённых событий в лог-файл.
 */
public class Logger {
    /**
     * Формат даты, в котором логировщик указывает время события при его протоколировании.
     */
    private static final SimpleDateFormat logTime = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    /**
     * Логописец, который используется данным логировщиком.
     */
    private final LogWriter writer;
    /**
     * Включено ли протоколирование входящих сообщений.
     */
    private final boolean log_inbound;
    /**
     * Включено ли протоколирование исходящих сообщений.
     */
    private final boolean log_outbound;
    /**
     * Включено ли протоколирование сообщений, полученных Сервером от пользователя чата
     * и переданных другим пользователям. Программа-клиент не предполагает логирования такого типа.
     */
    private final boolean log_transferred;
    /**
     * Включено ли протоколирование ошибок и событий, связанных с работой программы.
     */
    private final boolean log_events;

    /**
     * Ссылка на файл, в который записывается лог. Может меняться по ходу работы программы
     * (когда пользователь меняет имя в ходе беседы).
     */
    private volatile File logFile;

    /**
     * Создаёт новый Логировщик с установленными настройками.
     * Запускает в себе поток Логописец.
     * Лог-файл изначально не инициализирован, его должен указать класс-хозяин.
     * @param log_inbound     логируются ли входящие сообщения.
     * @param log_outbound    логируются ли исходящие сообщения.
     * @param log_transferred логируются ли пересланные сообщения (для сервера).
     * @param log_events      логируются ли ошибки и события.
     */
    public Logger(boolean log_inbound, boolean log_outbound, boolean log_transferred, boolean log_events) {
        this.log_inbound = log_inbound;
        this.log_outbound = log_outbound;
        this.log_transferred = log_transferred;
        this.log_events = log_events;
        writer = new LogWriter(this, 128);
        writer.start();
    }

    /**
     * Устанавливает адрес файла, в который должны записываться новые события.
     * Если предлагаемое имя не является допустимым, выводит об этом уведомление
     * и оставляет поле неизменным. Затем, если новое имя установлено,
     * а такой файл ещё не существует, пытается создать такой файл.
     * Если создание не венчается успехом, уведомляет об этом.
     * @param fileName имя (адрес) лог-файла.
     */
    public void setLogFile(String fileName) {
        try {
            if (!Message.isAcceptableName(fileName))
                throw new IllegalArgumentException("Неприемлемое имя!");
            logFile = new File(fileName);
            if (logFile.createNewFile()) {
                String created = "Лог %s создан%n".formatted(fileName);
                logEvent(created);
            }
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("Невозможно создать лог. " + e.getMessage());
        }


    }

    /**
     * Логирует сообщение согласно шаблону, если включено логирование входящих.
     * @param inboundMessage протоколируемое сообщение.
     */
    public void logInbound(Message inboundMessage) {
        if (log_inbound)
            log(messageToLog("получено", inboundMessage));
    }
    /**
     * Логирует сообщение согласно шаблону, если включено логирование исходящих.
     * @param outboundMessage протоколируемое сообщение.
     */
    public void logOutbound(Message outboundMessage) {
        if (log_outbound)
            log(messageToLog("отослано", outboundMessage));
    }
    /**
     * Логирует сообщение согласно шаблону, если включено логирование передаваемых.
     * @param transferredMessage протоколируемое сообщение.
     */
    public void logTransferred(Message transferredMessage) {
        if (log_transferred)
            log(messageToLog("переправлено", transferredMessage));
    }
    /**
     * Логирует полученную строку, если включено логирование событий.
     * @param event протоколируемое событие.
     */
    public void logEvent(String event) {
        if (log_events)
            log(event);
    }

    /**
     * Сообщает ссылку на файл, который следует использовать для логирования новых событий.
     * @return значение по́ля logFile.
     */
    public File getLogFile() {
        return logFile;
    }

    /**
     * Посылает знак прерывания Логописцу, тем самым знаменуя
     * окончание протоколирования событий.
     */
    public void stopLogging() {
        System.out.println("STOP_LOGGING invoked"); // monitor
        logEvent("Завершение протоколирования.");
        writer.interrupt();
    }

    /*
        Внутренние вспомогательные методы.
     */
    /**
     * Оформляет полученную строку как запись для лога
     * (т.е. добавляет к ней текущую дату в принятом в логировщике формате)
     * и ставит её в очередь к логописцу.
     * @param event текст, который логируется.
     */
    private void log(String event) {
        String logEntry = "%s : %s\n"
                .formatted(logTime.format(new Date()), event);

        writer.placeInQueue(logEntry);
    }
    /**
     * Функция генерации строкового представления сообщения для лога.
     * @param prefix  каким словом описывается действие над сообщением.
     * @param message описываемое сообщение.
     * @return  текстовое представление сведений о сообщении и его содержимого.
     */
    private String messageToLog(String prefix, Message message) {
        StringBuilder logged = new StringBuilder(prefix);
        logged.append(" от ").append(message.getSender() == null || "".equals(message.getSender()) ?
                        "сервера" : message.getSender())
                .append(" для ").append(message.getAddressee() == null ?
                        (message.isRequest() ?
                                "сервера" : "всех") : message.getAddressee()).append(": ");

        if (message.isStopSign())
            logged.append(" <STOP_SIGN> ");

        logged.append(switch (message.getType()) {
            case LIST_REQUEST -> "<LIST_REQUEST>";
            case REG_REQUEST -> "<REG_REQUEST>";
            case EXIT_REQUEST -> "<EXIT_REQUEST>";
            case SHUT_REQUEST -> "<SHUT_REQUEST>";
            default -> "";
        });

        if (message.getMessage() != null)
            logged.append(message.getMessage());

        return logged.toString();
    }

}
