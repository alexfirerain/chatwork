package server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final File settingsSource = new File("settings.ini");
    public static final int nickLengthLimit = 15;
    private final static int port_default = 7777;
    private final static byte[] password_default = "0000".getBytes();

    private final ExecutorService connections = Executors.newCachedThreadPool();
    final Dispatcher users = new Dispatcher();

    private final int PORT;
    private final byte[] PASSWORD;

    public Server() {
        PORT = port_default;
        PASSWORD = password_default;
    }

    public Server(File settingFile) {
        Map<String, String> settings;
        try {
            settings = readSettings(settingFile.toPath());
        } catch (IOException e) {
            e.printStackTrace();
            PORT = port_default;
            PASSWORD = password_default;
            return;
        }
        try {
            if (settings.get("PORT") != null)
                PORT = Integer.parseInt(settings.get("PORT"));
            else
                PORT = port_default;
        } catch (NumberFormatException e) {
            PORT = port_default;
        }

        if (settings.get("PASSWORD") != null)
            PASSWORD = settings.get("PASSWORD").getBytes();

    }

    public boolean wordPasses(byte[] password) {
        return Arrays.equals(PASSWORD, password);
    }

    private Map<String, String> readSettings(Path settingsSource) throws IOException {
        Map<String,String> settingsMap = new HashMap<>();
        String source = Files.readString(settingsSource);
        String[] lines = source.split(";");
        for (String line : lines) {
            int delim = line.indexOf("=");
            settingsMap.put(line.substring(0, delim).strip(),
                    line.substring(delim + 1).strip());
        }
        return settingsMap;
    }








}
