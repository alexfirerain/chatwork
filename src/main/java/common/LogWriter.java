package common;

import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LogWriter extends Thread {
    private final ArrayBlockingQueue<String> queue;
    private final Logger logSource;
    final Lock dormantWriter = new ReentrantLock(true);
    final Condition entryReady = dormantWriter.newCondition();

    public LogWriter(Logger logSource, int length) {
        this.logSource = logSource;
        queue = new ArrayBlockingQueue<>(length, true);
    }

    @Override
    public void run() {
        try {
            dormantWriter.lock();
            // пока не остановлен и пока есть ещё очередь
            while (!isInterrupted() || !queue.isEmpty()) {
                if (queue.isEmpty())
                    entryReady.awaitUninterruptibly();

                String logEntry = queue.poll();
                if (logEntry != null)
                    try (FileWriter logger = new FileWriter(logSource.getLogFile(), true)) {
                        logger.write(logEntry);
                        logger.flush();
                    } catch (IOException e) {
                        System.out.println("Что-то лог не пишется! -> " + e.getMessage());
                    }
            }
        } finally {
            dormantWriter.unlock();
        }
    }

    public void placeInQueue(String entry) {
        try {
            dormantWriter.lock();
            queue.add(entry);
            entryReady.signal();
        } finally {
            dormantWriter.unlock();
        }
    }

}
