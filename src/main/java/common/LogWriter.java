package common;

import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

public class LogWriter extends Thread {
    private final ArrayBlockingQueue<String> queu;
    private final Logger logSource;


    public LogWriter(Logger logSource, int length) {
        this.logSource = logSource;
        queu = new ArrayBlockingQueue<>(length, true);
    }

    @Override
    public void run() {
        while (!isInterrupted() || !queu.isEmpty()) {
            String logEntry = queu.poll();
            if (logEntry != null)
                try (FileWriter logger = new FileWriter(logSource.getLogFile(), true)) {
                    logger.write(logEntry);
                    logger.flush();
                } catch (IOException e) {
                    System.out.println("Что-то лог не пишется! -> " + e.getMessage());
                }
        }
    }

    public void placeInQueue(String entry) {
        queu.add(entry);
    }

}
