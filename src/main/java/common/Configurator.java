package common;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

/**
 * Служебный класс для чтения настроек из файла
 * или записи их в файл. Также хранит общие константы.
 */
public class Configurator {
    /**
     * Максимальная длина имени для регистрации на сервере.
     */
    public static final int nickLengthLimit = 15;

    /**
     * Карта настроек, соответствующая конфигурации.
     */
    private final Map<String,String> settings;

    /**
     * Инструментальная статическая функция. Определяет путь к файлу настроек, который должен использоваться.<p>
     * В качестве аргумента предполагается массив аргументов, с которым запущена программа.
     * Если программа запущена без аргументов, или первый аргумент не является адресом существующего файла,
     * запрашивает у пользователя ввод адреса, пока он не окажется именем существующего файла.
     * При этом является ли указанный файл действительно корректным файлом настроек – не проверяется.
     * @param args массив строковых аргументов (переданный программе при запуске).
     * @param usersInput источник ввода (сканер), предоставляемый объектом, использующим Конфигуратор.
     * @return путь к файлу, полученный из аргумента командной строки или из ввода пользователя.
     */
    public static Path identifySource(String[] args, Scanner usersInput) {
        String filePath;
        do if (args.length > 0 && Files.isRegularFile(Path.of(args[0]))) {
            filePath = args[0];
        } else {
            System.out.println("Источник настроек не обнаружен, введите имя файла вручную:");
            filePath = usersInput.nextLine();
        } while (!Files.isRegularFile(Path.of(filePath)));
        System.out.println("Настройки загружены из " + filePath);
        return Path.of(filePath);
    }

    /**
     * Инструментальная статическая функция. Сохраняет настройки, полученные в виде карты <строка, строка>,
     * в файл по указанному адресу.
     * @param settings карта сохраняемых настроек.
     * @param storage  путь к месту сохранения.
     */
    public static void writeSettings(Map<String, String> settings, Path storage) {
        StringBuilder string = new StringBuilder();
        for (Map.Entry<String, String> property : settings.entrySet())
            string.append(property.getKey())
                    .append(" = ")
                    .append(property.getValue())
                    .append(";\r\n");
        try (FileWriter writer = new FileWriter(String.valueOf(storage), false)) {
            writer.write(string.toString());
            writer.flush();
        } catch (IOException e) {                                           // пробросить?
            String error = "Не удалось сохранить настройки в " + storage;
            System.out.println(error);
            e.printStackTrace();
        }
    }

    /**
     * Создаёт новый Конфигуратор на основе указанного файла настроек.
     * @param settingsFile путь к файлу настроек.
     */
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
     * Возвращает опционально строку, соответствующую значению запрошенного параметра.
     * @param name имя параметра.
     * @return  опциональ со значением параметра, либо,
     * если параметр отсутствует в файле настроек, пустую опциональ.
     */
    public Optional<String> getStringProperty(String name) {
        return Optional.ofNullable(settings.get(name));
    }

    /**
     * Возвращает опционально int, соответствующий значению запрошенного параметра.
     * @param name имя параметра.
     * @return  опциональ со значением параметра, либо, если параметр отсутствует
     * в файле настроек, или число не было распознано, пустую опциональ.
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

    /**
     * Возвращает опционально булево значение запрошенного параметра.
     * @param name имя параметра.
     * @return  пустую опциональ, если параметр отсутствует как таковой; либо же
     * опциональ с {@code истинно}, если текстовое значение параметра "true" (без учёта регистра),
     * а в любом ином случае опциональ с {@code ложно} - то есть аналогично {@link java.lang.Boolean#parseBoolean(String)}.
     */
    public Optional<Boolean> getBoolProperty(String name) {
        String stringValue = settings.get(name);
        if (stringValue == null)
            return Optional.empty();
        return Optional.of(Boolean.parseBoolean(stringValue));
    }

    /**
     * Внутренняя статическая функция. Читает настройки из файла и представляет их в виде карты "параметр-значение".
     * @param settingsSource адрес читаемого файла.
     * @return  карту настроек, трактуя строки, разделённые ";", как "пара ключ-значение",
     * а внутри пары трактуя то, что идёт до первого "=", как ключ, а после него - как значение.
     * @throws IOException при ошибке чтения файла.
     */ 
    private static Map<String, String> readSettings(Path settingsSource) throws IOException {
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
}
