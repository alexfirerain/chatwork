package common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Configurator {
    Map<String,String> settings;

    public Configurator(Path settingsFile) throws IOException {
        settings = readSettings(settingsFile);
    }

    /**
     * Читает настройки из файла и представляет их в виде карты "параметр-значение".
     * @param settingsSource адрес читаемого файла.
     * @return  карту настроек, трактуя строки, разделённые ";", как "пара ключ-значение",
     * а внутри пары трактуя то, что идёт до первого "=", как ключ, а после него - как значение.
     * @throws IOException при ошибке чтения файла.
     */
    public static Map<String, String> readSettings(Path settingsSource) throws IOException {
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

    public Optional<String> getStringProperty(String name) {
        return Optional.ofNullable(settings.get(name));
    }
    public Optional<Integer> getIntProperty(String name) {
        String stringValue = settings.get(name);
        if (stringValue == null)
            return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(stringValue));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

    }
}
