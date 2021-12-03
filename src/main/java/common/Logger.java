package common;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    private static final SimpleDateFormat logTime = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    private final LogWriter writer;
    private final boolean log_inbound;
    private final boolean log_outbound;
    private final boolean log_transferred;
    private final boolean log_events;

    private volatile File logFile;

    public Logger(boolean log_inbound, boolean log_outbound, boolean log_transferred, boolean log_events) {
        this.log_inbound = log_inbound;
        this.log_outbound = log_outbound;
        this.log_transferred = log_transferred;
        this.log_events = log_events;
        writer = new LogWriter(this, 128);
        writer.start();
    }

    public void setLogFile(String fileName) {
        logFile = new File(fileName);

        if (!logFile.exists()) try {
            if (logFile.createNewFile()) {
                System.out.printf("Лог %s создан", fileName);
            }
        } catch (IOException e) {
            System.out.println("Невозможно создать лог. " + e.getMessage());
        }

    }

    public void log(String event) {
        String logEntry = "%s : %s\n"
                .formatted(logTime.format(new Date()), event);

        writer.placeInQueue(logEntry);


//        try(FileWriter logger = new FileWriter(logFile, true)) {
//            logger.write(logEntry);
//            logger.flush();
//        } catch (IOException e) {
//            System.out.println("Что-то лог не пишется! -> " + e.getMessage());
//        }
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

    public File getLogFile() {
        return logFile;
    }

    private String determineSender(Message message) {
        return message.getSender() == null ? "сервера" : message.getSender();
    }
    public void stopLogging() {
        writer.interrupt();
    }

    public boolean dontLogTransferred() {
        return log_transferred;
    }

}
