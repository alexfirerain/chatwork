package common;

import java.io.File;
import java.nio.file.Path;

public class Logger {
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
        logFile = new File(String.valueOf(Path.of(fileName)));
    }

    public void log(String event) {

    }

}
