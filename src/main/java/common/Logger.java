package common;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    private final boolean log_inbound;
    private final boolean log_outbound;
    private final boolean log_transferred;
    private final boolean log_errors;

    private File logFile;

    public Logger(boolean log_inbound, boolean log_outbound, boolean log_transferred, boolean log_errors) {
        this.log_inbound = log_inbound;
        this.log_outbound = log_outbound;
        this.log_transferred = log_transferred;
        this.log_errors = log_errors;
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
        StringBuilder logEntry = new StringBuilder();
        try(FileWriter logger = new FileWriter(logFile, true)) {
            logEntry.append(timeFormat.format(new Date()))
                    .append(" : ").append(event).append("\n");
            logger.write(logEntry.toString());
            logger.flush();
        } catch (IOException e) {
            System.out.println("Что-то лог не пишется! -> " + e.getMessage());
        }
    }

    public void logInbound(Message inboundMessage, String sender) {
        if (!log_inbound) return;
        log("получено от " + sender + ": " + inboundMessage);
    }
    public void logOutbound(Message outboundMessage, String sender) {
        if (!log_outbound) return;
        log("отослано от " + sender + ": " + outboundMessage);
    }
    public void logOutbound(Message outboundMessage) {
        if (!log_outbound) return;
        log("отослано от " + outboundMessage.getSender() + ": " + outboundMessage);
    }

    public void logTransferred(Message transferredMessage, String sender) {
        if (!log_transferred) return;
        log("переправлено от " + sender + ": " + transferredMessage);
    }
    public void logEvent(String event) {
        if (!log_errors) return;
        log(event);
    }



}
