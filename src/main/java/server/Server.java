package server;

import connection.Message;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final File settingsSource = new File("settings.txt");
    public static final int nickLengthLimit = 15;


    private final int PORT;
    private final byte[] PASSWORD;
    private final ExecutorService connections;
    final Dispatcher users = new Dispatcher();

    public Server() {
        PORT = 7777;
        PASSWORD = "0000".getBytes();
        connections = Executors.newCachedThreadPool();
    }









}
