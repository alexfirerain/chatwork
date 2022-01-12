# Многопользовательский чат

Программный комплекс состоит из двух приложений: Чат-Сервер и Чат-Клиент. Функционал заключается
в общении нескольких пользователей **Клиента** посредством текстовых сообщений в консоли,
которое обеспечивается их подключением к общему **Серверу**.

Исходный код разделён на три пакета:
1. _common_ содержит описание протокола взаимодействия (классы _MessageType_ и _Message_)
и утилиты, необходимые для работы обоих приложений (классы _Configurator_, _Logger_ и _LogWriter_).
2. _server_ содержит классы **Сервера**: _Server_, _Dispatcher_, _Connection_, а также _TextConstants_.
3. _client_ содержит классы **Клиента**: _Client_ и _Receiver_.

### Запуск приложений
**Сервер** запускается через метод `server.Server.main()`, **Клиент** запускается через метод
`client.Client.main()`. Предполагается работа нескольких пользователей клиентской части 
с разных машин. Для демонстрационной работы программы в IntelliJ IDEA для каждого клиента
создаётся отдельная конфигурация запуска.

Параметры запуска сервера и клиента загружаются из конфигурационных файлов:
в начале исполнения их методов `main()` создаётся экземпляр соответствующего класса на
основе значений, полученных из указанного файла. Формат файла настроек см. в разделе про **Конфигуратор**.

Для **Сервера** в данной реализации используется только файл настроек по умолчанию: _settings.ini_,
ссылка на него хранится в статической константе. Если в файле отсутствуют какие-либо настройки,
они восполняются значениями по умолчанию.

Для **Клиента** основным способом задания файла настроек является его указание в качестве
аргумента методу `main()` в командной строке или в соответствующей строке конфигурации
запуска в IDEA. Данная реализация включает три тестовых файла: _client_1.ini_, _client_2.ini_ и
_client_3.ini_.
В случае запуска **Клиента** без аргумента, или если переданный аргумент не является именем
существующего файла, приложение будет предлагать указать его вручную, пока не будет получено
имя доступного файла. Если указанный файл таки не окажется корректным для приложения файлом
настроек, все параметры будут заполнены по умолчанию. Также по умолчанию будут выставлены
все настройки, которые не будут найдены в файле.

## Протокол взаимодействия и формат сообщений
Взаимодействие **Клиентов** и **Сервера** осуществляется посредством обмена **Сообщениями**
(объектами класса _Message_) через устанавливаемые Клиентами до Сервера сокетные http-соединения.

### класс _Message_ - **Сообщение**
##### Поля́
Сериализуемый класс _Message_ содержит четыре по́ля:
* `final private MessageType type` = **Тип Сообщения**, определяющий алгоритм его обработки при получении
(описание типов см. ниже).
* `final private String sender` = отправитель сообщения: для порождаемых Клиентом – текущее имя пользователя;
для серверных сообщений - `null`, либо, если это условный сигнал на завершение соединения,
пустая строка (`""`).
* `private String addressee` = адресат сообщения: для публичных сообщений от Чат-Клиента
(то есть адресованных всем подключённым участникам беседы) и сообщений-запросов – `null`,
для персональных сообщений определённому пользователю – зарегистрированное на Чат-Сервере
имя получателя. Чат-Сервер в серверном сообщении заполняет это поле `null`, пока 
пользователь не зарегистрирован в **Диспетчере** – после же когда регистрация состоялась,
получатель должен быть явно указан, поэтому присутствует метод `.setAdressee(String имя_получателя)` 
для выставления адресатов, когда одинаковое серверное сообщение рассылается всем 
подключённым пользователям. По значению этого поля в серверном сообщении Чат-Клиент определяет, 
что запрошенное (при регистрации либо по ходу беседы) имя принято и зарегистрировано на Чат-Сервере.
* `final private String message` = собственно текст сообщения; в сообщениях-запросах
(т.е. кроме серверных, публичных и персональных) - `null`.
##### Методы
Кроме методов доступа к полям, класс _Message_ предоставляет статические методы для создания
экземпляров (открытого конструктора в классе нет):
* `public static Message fromClientInput(String inputText, String sender)` Создаёт на клиентской
стороне экземпляр сообщения на основе пользовательского ввода и указанного имени отправителя 
(берётся из имени пользователя).
* `public static Message fromServer(String messageText, String receiver)` Создаёт на серверной стороне
серверное сообщение с указанным текстом, адресованное указанному получателю.
* `public static Message fromServer(String messageText)` Создаёт на сервере серверное сообщение с указанным
текстом без указания получателя (предполагается, что получатели будут выставлены отдельно).
* `public static Message registering(String putName)` Создаёт на клиентской стороне сообщение с запросом 
регистрации указанного имени.
* `public static Message stopSign(String message, String recipient)` Создаёт стоп-сообщение с указанным текстом
для указанного получателя.

А также вспомогательные методы:
* `public static boolean isAcceptableName(String name)` Инструментальная функция, проверяющая, является ли
строка допустимой в качестве регистрируемого имени пользователя. В настоящей реализации используется проверка
соответствия регулярному выражению `"[\\p{L}]+\\d*\\s*"`.
* `public boolean isStopSign()` - является ли сообщение сигналом о завершении.
* `public boolean isServerMessage()` - относится ли сообщение к типу `SERVER_MSG`.
* `public boolean isTransferable()` - относится ли сообщение к типу `TXT_MSG` или `PRIVATE_MSG`.
* `public boolean isRequest()` - относится ли сообщение к типам `REG_REQUEST`, `LIST_REQUEST`, `EXIT_REQUEST` или `SHUT_REQUEST`

* метод `.toString()` используется для представления **Сообщения** в консоли. Основной шаблон
включает _имя отправителя_ + " > " (если отправитель не `null`) + _текст сообщения_ (если оно не `null`).
Для типов кроме публичного этот шаблон предваряется отдельной строкой с явным описанием типа для личных
и серверных или с условным обозначением типа для сообщений-запросов.

### Типы сообщений
Всё взаимодействие обеспечивается с помощью семи **Типов Сообщения**, перечисленных в классе _MessageType_:
1. `SERVER_MSG` = **Серверное Сообщение**, т.е. информационное сообщение от Чат-Сервера.
2. `TXT_MSG` = **Публичное Сообщение**, т.е. обычное сообщение, посылаемое пользователем Чат-Клиента
и затем рассылаемое Чат-Сервером всем подключённым участникам беседы, кроме отправителя.
3. `PRIVATE_MSG` = **Частное Сообщение**, посылаемое пользователем Чат-Клиента и затем пересылаемое
Чат-Сервером указанному в качестве получателя участнику, если таковой подключён.
4. `REG_REQUEST` = запрос на регистрацию от пользователя.
5. `LIST_REQUEST` = запрос от клиента на получение списка подключённых пользователей.
6. `EXIT_REQUEST` = запрос от клиента на выход из чата.
7. `SHUT_REQUEST` = запрос от клиента на остановку работы сервера. В данной реализации это
единственный корректный способ остановить Чат-Сервер.

В реализации используется обращение к элементам перечисления по `.ordinal()`, это требует внимания 
при внесении изменений в типы сообщения.

Типы сообщений делятся на три группы сообразно своему происхождению и назначению:
* _Серверные сообщения_ отправляются от сервера клиентам.
* _Передаваемые сообщения_ отправляются клиентами и перенаправляются сервером клиентам же.
* _Сообщения-запросы_ отправляются клиентами на сервер в качестве команд.

На принадлежность сообщения к группе указывают его методы `.isServerMessage(),` `.isTransferable()` и `.isRequest()`.

Стоп-Сообщение - это разновидность серверного сообщения с пустой строкой в поле отправителя.
Клиент, получив такое сообщение, выводит его текст (если он присутствует) в консоль и
разрывает соединение. Свойство оглашается через `.isStopSign()`. 



## Работа серверной части
### Архитектура
Серверная часть состоит из следующих компонентов:
* класс _Server_ – **Сервер** слушает за входящие на серверный порт подключения.
* класс _Connection_ – **Соединение** обслуживает обмен сообщениями с конкретным подключённым клиентом.
* класс _Dispatcher_ – **Диспетчер** хранит карту соответствий зарегистрированных имён и соединений,
осуществляет направление сообщений между участниками общения и обработку сообщений-запросов.
* класс _TextConstants_ - используется в качестве ресурса строковых литералов, в потенциале может
предоставлять локализации пользовательского интерфейса.

По инициализации, **Сервер** создаёт в себе новые **Диспетчер** и **Логировщик** и
начинает слушать входящие подключения на порту, указанном в поле `server.Server.PORT`.
Обнаружив подключение и установив сокетное соединение с клиентом, он создаёт и запускает в обойме потоков
новый объект класса **Соединение**, передавая в него только что полученный сокет и ссылку на себя. Затем,
если не снят соответствующий флажок, возвращается к началу цикла ждать очередного подключения.

Экземпляр-соединение начинает работать с подключённым клиентом, сперва запуская _процедуру регистрации_, затем работая
на приём поступающих от него сообщений (которые обрабатывает самостоятельно либо передаёт Диспетчеру)
и отправку сообщений (порождённых самим соединением или Диспетчером). Соединение находится в одном из двух режимов: 
локальный и глобальный. В _локальном режиме_ обработка поступившего сообщения проходит в самом соединении, в _глобальном_
же сообщение передаётся на Диспетчер. В локальном режиме соединение находится в начале установления сеанса,
когда идёт процедура регистрации, по окончанию которой соединение входит в глобальный режим, основной режим работы чат-хаба.
В данной реализации переход в локальный режим используется только для процедуры запроса пароля на доступ
к серверу (в данной реализации - к команде выключения).

Диспетчер содержит в себе реестр зарегистрированных подключённых клиентов и предоставляет к нему доступ.
Также он получает от соединений сообщения и обрабатывает их сообразно типу. Получая _передаваемое сообщение_,
он пересылает его указанному адресату или всем собеседникам пославшего (если сообщение _публичное_). Получая
_сообщение-запрос_, запускает соответсвующую процедуру. При этом он может генерировать исходящие _серверные
сообщения_: уведомления участникам, ответы на запросы.

Команда на завершение сеанса, соответствующая пользовательскому вводу `"/exit"`, отправляется
сообщением типа `EXIT_REQUEST`. Диспетчер, получив его, отключает запросившего выход участника
и рассылает оставшимся информационное серверное сообщение.

Остановка Сервера в данной реализации возможна только через отправку ему от Клиента сообщения
типа `SHUT_REQUEST`, соответствующего пользовательскому вводу `"/terminate"`. Получив такой 
запрос, Диспетчер делегирует разобраться с ним приславшему экземпляру Соединения. Оно входит
в локальный режим, предлагает прислать пароль (задаваемый серверу в файле настроек), получает
его из следующего принятого сообщения и в виде массива байтов вкладывает его в метод
`server.Server.stopServer()`, затем возвращается в глобальный режим. Если предложенный ключ 
совпал с замком, Сервер выходит из цикла ожидания подключений (для чего провоцирует сам с собой
соединение), реализованного в методе `.listen()`, и переходит к процедуре `.exit()`: распоряжается
Диспетчеру завершить все сеансы, пытается завершить все потоки соединений и останавливает логирование.
Диспетчер рассылает всем подключённым уведомления со стоп-сигналом и отключает их.

### класс _Server_ - **Сервер**
Служит для инициализации и интеграции компонентов серверной части и для установления соединений
с клиентами.

#### поля:
##### _статические константы для значений по умолчанию_:
* `private static final Path settingsSource = Path.of("settings.ini")` путь к файлу настроек.
В данной реализации является единственным способом задания источника настроек.
* `private static final String host_default = "localhost"` имя сервера.
* `private static final int port_default = 7777` порт для приходящих соединений.
* `private static final byte[] password_default = "0000".getBytes()` пароль для управления.
##### _константы, задаваемые экземпляру сервера при создании_:
* `public final String HOST` и `public final int PORT` = адрес и порт, по которым сервер доступен в сети.  
* `private final byte[] PASSWORD` = пароль для подтверждения управляющих команд на сервере.
* `private final boolean LOG_INBOUND, LOG_OUTBOUND, LOG_TRANSFERRED, LOG_EVENTS` = настройки логировщика:
будут ли протоколироваться соответственно: входящие сообщения (запросы), исходящие (серверные) сообщения,
переправляемые сообщения и возникающие события (установление и закрытие соединений, регистрация, 
перерегистрация и выход участников, а также всевозможные ошибки).
##### _необходимые объекты, создаваемые при инициализации_:
* `private final ExecutorService connections` = пул потоков адаптивного размера, в котором будут исполняться
потоки-соединения.
* `final Dispatcher users` = нужный для работы серверной части в целом экземпляр Диспетчера.
* `final Logger logger` = используемый всей серверной частью экземпляр Логировщика.
##### _флаг режима прослушивания_
* `private volatile boolean listening` = находится ли сервер в состоянии ожидания новых подключений.

#### конструкторы
После назначения полей генерируется Логировщик на основе установленных настроек. Имя лог-файла устанавливается
"server.log". Создаётся чистый Диспетчер.
* `public Server()` = не используемый в реализации конструктор, создающий экземпляр с настройками по умолчанию.
Хост, порт и пароль принимаются из глобальных констант, `LOG_OUTBOUND` принимается true, остальные
настройки логировщика false.
* `public Server(String host, int port, byte[] password)` = также не используется в реализации.
Хост, порт и пароль принимаются из аргументов, а настройки логирования, как в предыдущем конструкторе,
по умолчанию.
* `public Server(Path settingFile)` = практический конструктор, загружающий с помощью конфигуратора
настройки из файла по указанному пути. Если файл не окажется существующим или корректным, все
настройки будут приняты по умолчанию. Если какие-то настройки не будут из него прочитаны, также
по умолчанию будут восполнены.

#### методы
* `public static void main()` → Запуск сервера происходит с этой точки, метод олицетворяет жизненный цикл сервера:
  1. создание экземпляра Сервера (на основе указанного файла настроек);
  1. запуск метода `.listen()` - в цикле ожидание и установление подключений;
  1. по выходу из рабочего цикла – процедура `.exit()`.

* `private void listen()` - рабочий цикл, продолжающийся пока `listening = true`: обнаружение нового подключения,
его логирование и запуск в пуле нового потока типа Соединение. Если требуется запуск сервера из другого класса,
метод должен будет быть объявлен открытым. 

* `private void exit()` - процедура финализации, включающая закрытие соединений и остановку запущенных потоков.

* `public void stopServer(byte[] gotPassword)` - открытый метод для остановки сервера. Принимает пароль и,
если он совпал с заданным при создании сервера, устанавливает `running = false` и провоцирует фантомным 
соединением выход из рабочего цикла.



### класс _Dispatcher_ - **Диспетчер**
Служит для контроля списка подключённых, пересылки сообщений и прочей логики взаимодействия
клиентов с хабом.

#### поля
* `private final Server host` = ссылка на сервер, создавший этот диспетчер.
* `private final Map<String, Connection> users` = "реестр": карта <имя_пользователя, ссылка_на_соединение>.
* `private final Logger logger` = логировщик, используемый на сервере в целом.

#### конструктор
* `public Dispatcher(Server host)` инициализирует пустой реестр, запоминает ссылку на сервер
и на логер.

#### _Методы работы с реестром участников:_
* `public boolean addUser(String userName, Connection connection)` регистрирует
участника; возвращает true, если успешно добавлен (т.е. если имя является допустимым, отсутствовало
в реестре, а теперь появилось). Логирует успех или отказ регистрации.
* `public Set<String> getUsers()` сообщает набор участников.
* `public Set<String> getUsersBut(String aUser)` сообщает набор участников за исключением одного.
* `public Connection getConnectionForUser(String user)` даёт ссылку на соединение, ассоциированное с участником.
* `public String getUserForConnection(Connection connection)` даёт имя участника, ассоциированного с соединением.

#### _Методы отсылки или рассылки сообщений участникам:_
* `private void send(Message message, String username)` вкладывает сообщение в метод `.sendMessage()`
соединения, найденного по имени пользователя. Обнаружив, что соединение с клиентом закрыто,
вызывает процедуру его отключения.
* `private void send(Message msg, boolean toLog)` надстройка над `.send(Message, String)`, отправляет
сообщение тому пользователю, который указан в сообщении как получатель. Если получатель не указан, не 
делает ничего. Если toLog равно true, логирует сообщение как _отправленное_.
* `private void send(Message msg)` эквивалентно `.send(msg, true)`.
* `private void forward(Message message)` если получатель не указан (т.е. _публичное_), рассылает сообщение
всем подключённым участникам, кроме его отправителя. Если сообщение _частное_, отправляет его адресату. 
Логирует как _переданное_.
* `private void broadcast(Message message)` рассылает сообщение всем подключённым участникам, при этом явно
заполнив в нём перед отправкой поле получателя. Логирует как _отправленное_.
* `private void castWithExclusive(Message generalMessage, String exclusiveOne, Message specialMessage)`
рассылает первое сообщение всем подключённым участникам, кроме указанного, а ему второе сообщение. Явно
проставляет всех получателей, логирует оба сообщения как _отправленные_.

#### _Методы взаимодействия с клиентом:_
* `public void operateOn(Message gotMessage, Connection source)` → метод взаимодействия Диспетчера со входящим
сообщением - Соединение передаёт сюда сообщение и ссылку на себя. Получение _серверных_ сообщений не предполагается.
Получив _передаваемое_ сообщение, отдаёт его в метод `.forward()`. Получив `LIST_REQUEST`, вызывает метод `.sendUserList()`
с именем отправителя. Получив `REG_REQUEST`, вызывает метод `.changeName()` с именем отправителя и ссылкой на
соединение-источник. Получив `EXIT_REQUEST`, вызывает метод `.goodbyeUser()` с именем отправителя. Получив
`SHUT_REQUEST`, вызывает у соединения-источника процедуру `.getShut()`.
* `public void closeSession()` вызывает для каждого пользователя из реестра процедуру `.disconnect()` с текстом
уведомления о завершении работы.
* `public void greetUser(String greeted)` высылает новоподключённому участнику приветственное сообщение с 
информацией о чате, а остальным подключённым участникам сообщение, уведомляющее о подключении участника.
* `public void goodbyeUser(String username)` запускает для указанного участника процедуру `.disconnect()` с
прощальным текстом. Если она вернула `true`, рассылает подключённым участникам сообщение, уведомляющее об 
отключении участника.
* `private void changeName(String newName, Connection connection)` добавляет в реестр новое имя, ассоциируя его
со ссылкой на соединение. Если добавление проходит успешно, удаляет из реестра имя, которое было ключом к
данному соединению до того, и рассылает всем участникам сообщение, уведомляющее о смене имени одним из них.
Если добавление не проходит успешно (т.е. если предлагаемое имя не является допустимым либо уже зарегистрировано
для какого-то соединения), отсылает об этом уведомление тому, кто присылал запрос, (на старое имя).
* `private boolean disconnect(String username, String farewell)` отсылает указанному пользователю стоп-сообщение
с указанным текстом, после этого закрывает ассоциированное с ним соединение, удаляет его из реестра и 
возвращает `true`. Если что-то из этого обломилось ошибкой, логирует её и возвращает `false`.
* `private void sendUserList(String requesting)` высылает указанному пользователю сообщение со списком
зарегистрированных в данный момент в реестре участников.

#### _Вспомогательные методы-генераторы текста:_
* `private String welcomeText(String greeted)` возвращает текст, приветствующий указанного пользователя,
сообщающий актуальный сетевой адрес чата и перечисляющий пользовательские команды и подключённых участников. 
* `private String getUserListing()` возвращает текст, сообщающий количество подключённых и их имена.



### класс _Connection_ - **Соединение**
Является исполняемой обёрткой для сокета, способной принимать и отправлять сообщения класса Сообщение,
а также самостоятельно проводить некоторые интерактивные процедуры взаимодействия с клиентом.
Реализует интерфейсы Runnable и AutoCloseable, запуск обработки сокета в пуле соответствует методу `.run()`,
а закрытие соединения - методу `.close()`, также метод `isClosed()` соответствует тождественному методу сокета.

Работа соединения осуществляется в одном из двух режимов: _локальном_, когда новое входящее сообщение
обрабатывается локальным методом, и _глобальном_, когда оно передаётся на обработку Диспетчеру.
В _локальном_ режиме соединение находится в начале работы при исполнении метода `.requesterUser()`,
а также при исполнении метода обработки запроса на остановку сервера - `.getShut()`.

#### поля:
* `private final Server host` ссылка на Сервер, установивший это Соединение.
* `private final Dispatcher dispatcher` ссылка на Диспетчер сервера.
* `private final Socket socket` сокетное соединение, обёрткой для которого служит этот объект
* `private final Logger logger` ссылка на Логировщик сервера.
* `private ObjectInputStream messageReceiver` входящий объектный поток от сокета.
* `private ObjectOutputStream messageSender` исходящий объектный поток на сокет.
* `private boolean localMode = true` показатель, находится ли Соединение в локальном режиме.

#### конструктор
* `public Connection(Server host, Socket socket)` устанавливает ссылку на обслуживаемое экземпляром
сокетное соединение и на сервер, также разрешает ссылки на Диспетчер и Логировщик серверной стороны.

#### методы:
* `@Override public void run()` → жизненный цикл соединения: 
  1. получение входящего и исходящего потоков из сокета;
  2. запуск процедуры регистрации пользователя;
  3. пока сокет не закрыт, проверяет, находится ли в _глобальном_ режиме: если да, то получает
очередное сообщение и передаёт его Диспетчеру; если нет, то очередное сообщение принимает и
обрабатывает один из локальных методов.
  4. обнаружив, что соединение закрыто, завершает исполнение.
* `private void registerUser()` процедура регистрации нового участника: отсылает в новоустановленное
соединение пробное сообщение, считывает имя отправителя из полученного сообщения и пытается
зарегистрировать его в диспетчере. Как только эта попытка венчается успехом, выходит из локального
режима и говорит диспетчеру провести обряд приветствия нового участника. Но пока попытки зарегистрировать
имя не успешны, шлёт регистрирующемуся об этом уведомления. Логирует отсылаемые сообщения (входящие
логируются на уровне метода `.receiveMessage()`, так как ожидаются только _запросные_ сообщения).
* `public void sendMessage(Message message) throws IOException` записывает сообщение в исходящий поток.
Метод открытый, так что используется другими классами.
* `private Message receiveMessage() throws IOException, ClassNotFoundException` дожидается из входящего
потока новое входящее сообщение и возвращает его. Если оно является _запросом_, также логирует его.
* `public void getShut()` процедура аутентификации для управления сервером: переходит в локальный режим,
уточняет у диспетчера зарегистрированное имя для данного соединения, генерирует, отправляет и логирует 
серверное сообщение с предложением прислать пароль; получает содержимое нового входящего сообщения 
как массив байтов и логирует фиктивное сообщение с замаскированным содержимым; затем возвращается в
глобальный режим и вызывает у сервера метод `.stopServer()`, передавая в него полученный массив. 
#### _вспомогательные методы_:
* `private void setLocalMode()` и `private void setGlobalMode()` устанавливают флаг `localMode` в `true` и
`false` соответственно.
* `@Override public void close() throws Exception` и `public boolean isClosed()` соответствуют аналогичным
методам обёрнутого сокета.
* `@Override  public String toString()` используется для строкового представления экземпляра.

### Завершение сеанса и остановка сервера
Сеанс работы клиента с хабом завершается в трёх случаях: клиент прислал запрос на отключение,
соединение оказалось по каким-либо причинам потеряно, либо сервер завершает работу. В любом случае
корректное завершение сеанса сопровождается удалением имени из реестра подключённых. При отключении
клиента по своей инициативе либо из-за разрыва соединения уведомление об этом получают оставшиеся
подключёнными пользователи. Также подключённые пользователи получают уведомление при завершении 
работы сервера.

Остановка сервера в данной реализации возможна только посредством отправки ему соответствующего 
запроса с последующее отправкой пароля, соответствующего заданному при инициализации сервера.


## Работа клиентской части
### Архитектура
Клиентская часть приложения состоит из двух классов:
* класс `Client` - **Клиент**, устанавливающий соединение до Сервера и отправляющий ему сообщения,
иже пользователь набирает в консоли;
* класс `Receiver` - **Приёмник**, отдельный поток, слушающий, обрабатывающий и отображающий 
пользователю в консоль сообщения от Сервера.








## Работа служебной части
### Конфигуратор



### Логировщик


