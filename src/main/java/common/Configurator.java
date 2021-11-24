package common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Configurator {
    private final Map<String,String> settings;

    public Configurator(Path settingsFile) {
        Map<String, String> settingsMap;
        try {
            settingsMap = readSettings(settingsFile);
        } catch (IOException e) {
            System.out.println("Настройки из файла не были загружены, используется пустой конфигуратор!");
            settingsMap = new HashMap<>();
        }
        settings = settingsMap;
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
            if (line.isBlank()) continue;
            int delim = line.indexOf("=");
            if (delim == -1) continue;
            String name = line.substring(0, delim).strip();
            String value = line.substring(delim + 1).strip();
            if (name.isBlank() || value.isBlank()) continue;
            settingsMap.put(name, value);
        }
        return settingsMap;
    }

    /**
     * Возвращает опционально строку, соответсвующую значению запрошенного параметра.
     * @param name имя параметра.
     * @return  опциональ со значением параметра, либо,
     * если параметр отсутствует в файле настроек, пустую опциональ.
     */
    public Optional<String> getStringProperty(String name) {
        return Optional.ofNullable(settings.get(name));
    }
    /**
     * Возвращает опционально int, соответсвующий значению запрошенного параметра.
     * @param name имя параметра.
     * @return  опциональ со значением параметра, либо, если параметр отсутствует
     * в файле настроек или число не было распознано, пустую опциональ.
     */
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
