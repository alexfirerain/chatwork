package common;

import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Логописец, держащий очередь протоколируемых событий и в отдельном потоке
 * записывающий их в тот файл, который указан в запустившем этот логописец логировщике.
 */
public class LogWriter extends Thread {
    /**
     * Очередь событий, которые нужно записать.
     */
    private final ArrayBlockingQueue<String> queue;
    /**
     * Логировщик, запустивший этот логописец.
     */
    private final Logger logSource;
    /**
     * Синхронизатор, обеспечивающий логописцу покой, когда нечего записывать.
     */
    final Lock dormantWriter = new ReentrantLock(true);
    /**
     * Условие, по которому логописец пробуждается к работе.
     */
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
        System.out.println("END running LogWriter");    // monitor
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
