package common;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

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

    public Logger(boolean log_inbound, boolean log_outbound, boolean log_transferred, boolean log_events) {
        this.log_inbound = log_inbound;
        this.log_outbound = log_outbound;
        this.log_transferred = log_transferred;
        this.log_events = log_events;
        writer = new LogWriter(this, 128);
        writer.start();
    }

    /**
     * Устанавливает адрес файла, в который должны записываться новые события,
     * и, если такой ещё не существует, создаёт его.
     * @param fileName имя (адрес) лог-файла.
     */
    public void setLogFile(String fileName) {
        logFile = new File(fileName);

        if (!logFile.exists()) try {
            if (logFile.createNewFile()) {
                System.out.printf("Лог %s создан%n", fileName);
            }
        } catch (IOException e) {
            System.out.println("Невозможно создать лог. " + e.getMessage());
        }

    }

    /**
     * Оформляет полученную строку как запись для лога
     * (т.е. добавляет к ней текущую дату в принятом в логировщике формате)
     * и ставит её в очередь к логописцу.
     * @param event текст, который логируется.
     */
    public void log(String event) {
        String logEntry = "%s : %s\n"
                .formatted(logTime.format(new Date()), event);

        writer.placeInQueue(logEntry);
    }

    public void logInbound(Message inboundMessage) {
        if (!log_inbound) return;
        log("получено от " + determineSender(inboundMessage) + ": " + inboundMessage);
    }

    public void logOutbound(Message outboundMessage) {
        if (!log_outbound) return;
        log("отослано от " + determineSender(outboundMessage) + ": " + outboundMessage);
    }

    public void logTransferred(Message transferredMessage) {
        if (!log_transferred) return;
        log("переправлено от " + determineSender(transferredMessage) + ": " + transferredMessage);
    }

    public void logEvent(String event) {
        if (!log_events) return;
        log(event);
    }

    /**
     * Сообщает ссылку на файл, который следует использовать для логирования новых событий.
     * @return значение по́ля logFile.
     */
    public File getLogFile() {
        return logFile;
    }


    private String determineSender(Message message) {
        return message.getSender() == null ? "сервера" : message.getSender();
    }
    public void stopLogging() {
        writer.interrupt();
    }

    /**
     * Показывает, включён ли в логировщике учёт пересланных сообщений
     * (чтобы определить, что их следует учитывать как исходящие,
     * если включён учёт исходящих, но выключен учёт пересланных).
     * @return значение по́ля {@code log_transferred}.
     */
    public boolean isLoggingTransferred() {
        return log_transferred;
    }

}
