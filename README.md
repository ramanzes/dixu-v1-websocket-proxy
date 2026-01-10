# Полная инструкция по развертыванию WebSocket Proxy с самоподписанным сертификатом

## Что это такое и для чего?

**WebSocket Proxy** — это система для удалённого доступа к локальным веб-сервисам через WebSocket туннель. 

### Основная идея:

У вас есть устройство (например, Raspberry Pi, домашний сервер, IoT устройство) с веб-интерфейсом, которое находится за NAT или файрволом. Вместо того чтобы пробрасывать порты или настраивать VPN, вы:

1. **Запускаете клиент** на локальном устройстве
2. Клиент подключается к **центральному серверу-прокси** через WebSocket (WSS)
3. **Пользователи обращаются** к серверу-прокси по URL типа `https://ваш-сервер:8443/p/device-14/`
4. **Сервер-прокси пересылает** запросы через WebSocket клиенту
5. **Клиент запрашивает** данные с локального веб-сервера и отправляет ответ обратно

### Схема работы:

```
Браузер пользователя
       ↓
https://192.168.122.76:8443/p/device-14/index.html
       ↓
[WebSocket Proxy Server] (на публичном сервере)
       ↓ (через WSS туннель)
[WebSocket Client] (на локальном устройстве за NAT)
       ↓
http://localhost:5000/index.html (локальный веб-сервер)
       ↓
Ответ идёт обратно по той же цепочке
```

### Примеры использования:

- 📡 Удалённый доступ к веб-интерфейсу домашнего сервера
- 🏠 Управление IoT устройствами (умный дом)
- 🖥️ Доступ к административным панелям устройств за NAT
- 🔧 Разработка и тестирование без проброса портов
- 📹 Доступ к IP-камерам и системам видеонаблюдения

### Как пользоваться после установки:

**Шаг 1:** Убедитесь что сервер запущен
```bash
systemctl status websocket-proxy
```

**Шаг 2:** Получите токен для нового устройства
```bash
curl -k https://ваш-сервер:8443/api/auth/token
```

**Шаг 3:** Настройте клиент на устройстве с полученным `deviceId` и `token`

**Шаг 4:** Запустите клиент
```bash
java -jar websocket-client.jar
```

**Шаг 5:** Откройте в браузере
```
https://ваш-сервер:8443/p/ваш-device-id/
```

Например: `https://192.168.122.76:8443/p/device-14/`

### Безопасность:

- ✅ Все данные идут через HTTPS/WSS (зашифровано SSL/TLS)
- ✅ Аутентификация через токены
- ✅ Один deviceId = одно подключение
- ✅ Нет открытых портов на клиентском устройстве

---

## Информация о проекте

**Серверная часть:** https://github.com/ramanzes/dixu-v1-websocket-proxy (ветка dev4)  
**Клиентская часть:** https://github.com/ramanzes/dixu-v1-websocket-client (ветка dev4)

**Технологии:**
- Сервер: Spring Boot 3.4.1, Java 21, WebSocket, SQLite
- Клиент: Java 11+, javax.websocket-api, Tyrus WebSocket Client
- SSL/TLS: Самоподписанный сертификат PKCS12

**Параметры сервера:**
- IP: 144.31.248.91
- Порт: 8443
- Endpoint: `/ws`
- Формат URL: `wss://144.31.248.91:8443/ws?deviceId=YOUR_DEVICE_ID&token=YOUR_TOKEN`

---

## ЧАСТЬ 1: ПОДГОТОВКА СЕРВЕРА

### 1.1 Подключение и обновление системы

```bash
ssh root@144.31.248.91

# Обновление системы
apt update && apt upgrade -y
```

### 1.2 Установка необходимого ПО

```bash
# Установка Java 21
apt install -y openjdk-21-jdk

# Проверка
java -version
# Должно показать: openjdk version "21.x.x"

# Установка Maven
apt install -y maven
mvn -version

# Установка Git
apt install -y git

# Установка firewall (если не установлен)
apt install -y ufw
```

---

## ЧАСТЬ 2: СОЗДАНИЕ SSL СЕРТИФИКАТА

### 2.1 Создание директории для сертификатов

```bash
mkdir -p /opt/websocket-proxy/certs
cd /opt/websocket-proxy/certs
```

### 2.2 Генерация PKCS12 keystore для сервера с SAN

**ВАЖНО:** Современные версии Java требуют наличия **SAN (Subject Alternative Name)** в сертификате!

```bash
keytool -genkeypair \
  -alias tomcat \
  -keyalg RSA \
  -keysize 2048 \
  -validity 3650 \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -storepass DfdyaIKbJl21FWt7Fq8 \
  -dname "CN=144.31.248.91, OU=WebSocket, O=Dixu, L=Moscow, ST=Moscow, C=RU" \
  -ext "SAN=IP:144.31.248.91"
```

**Что здесь используется:**
- `-alias tomcat` - из `server.ssl.keyAlias=tomcat`
- `-storetype PKCS12` - из `server.ssl.keyStoreType=PKCS12`
- `-keystore keystore.p12` - из `server.ssl.key-store=classpath:keystore.p12`
- `-storepass DfdyaIKbJl21FWt7Fq8` - из `server.ssl.key-store-password`
- `CN=144.31.248.91` - IP адрес сервера в качестве Common Name
- **`-ext "SAN=IP:144.31.248.91"`** - ⚠️ **КРИТИЧЕСКИ ВАЖНО!** Subject Alternative Name с IP адресом

**Примечание:** Если IP адрес сервера `192.168.122.76`, используйте его вместо `144.31.248.91` и в CN, и в SAN!

### 2.2.1 Проверка наличия SAN в сертификате

```bash
keytool -list -v \
  -keystore keystore.p12 \
  -storetype PKCS12 \
  -storepass DfdyaIKbJl21FWt7Fq8 | grep -A 3 "SubjectAlternativeName"

# Должно показать:
# SubjectAlternativeName [
#   IPAddress: 144.31.248.91
# ]
```

Если SAN отсутствует, сертификат НЕ БУДЕТ работать с современными версиями Java!

### 2.3 Экспорт публичного сертификата

```bash
keytool -exportcert \
  -alias tomcat \
  -keystore keystore.p12 \
  -storetype PKCS12 \
  -storepass DfdyaIKbJl21FWt7Fq8 \
  -file server-cert.crt
```

### 2.4 Создание truststore для клиента (не обязательно)

Клиент использует `disableSSLCertificateChecking()` и принимает любой сертификат, но для продакшена создадим truststore:

```bash
keytool -importcert \
  -alias websocket-server \
  -file server-cert.crt \
  -keystore client-truststore.jks \
  -storepass changeit \
  -noprompt
```

### 2.5 Проверка созданных файлов

```bash
ls -lh /opt/websocket-proxy/certs/

# Должны быть:
# keystore.p12            - для сервера
# server-cert.crt         - публичный сертификат (для справки)
# client-truststore.jks   - для клиента (опционально)
```

### 2.6 Просмотр содержимого keystore

```bash
keytool -list -v \
  -keystore keystore.p12 \
  -storetype PKCS12 \
  -storepass DfdyaIKbJl21FWt7Fq8
```

---

## ЧАСТЬ 3: РАЗВЕРТЫВАНИЕ СЕРВЕРНОГО ПРИЛОЖЕНИЯ

### 3.1 Клонирование репозитория

```bash
cd /opt/websocket-proxy
git clone https://github.com/ramanzes/dixu-v1-websocket-proxy.git
cd dixu-v1-websocket-proxy
git checkout dev4
```

### 3.2 Копирование keystore в ресурсы проекта

**Критически важно:** Keystore должен быть в `src/main/resources/` (classpath)

```bash
cp /opt/websocket-proxy/certs/keystore.p12 src/main/resources/

# Проверка
ls -lh src/main/resources/keystore.p12
```

### 3.3 Редактирование application.properties

Отредактируйте файл `src/main/resources/application.properties`:

```bash
nano src/main/resources/application.properties
```

**Ключевые настройки для изменения:**

```properties
# ВАЖНО: Измените на 0.0.0.0 чтобы слушать на всех интерфейсах
server.address=0.0.0.0

# Порт (оставьте как есть)
server.port=8443

# SSL (уже настроено, проверьте)
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=DfdyaIKbJl21FWt7Fq8
server.ssl.keyStoreType=PKCS12
server.ssl.keyAlias=tomcat

# Database (путь уже правильный)
spring.datasource.url=jdbc:sqlite:file:./mydb.sqlite

# Остальные настройки оставьте без изменений
```

**Внимание:** В текущем `application.properties` указан `server.address=192.168.0.19` - замените на `0.0.0.0` или ваш внешний IP `144.31.248.91`.

### 3.4 Сборка проекта

```bash
cd /opt/websocket-proxy/dixu-v1-websocket-proxy

# Сборка (займёт несколько минут)
mvn clean package -DskipTests

# Проверка созданного JAR
ls -lh target/websocket-proxy-0.0.1-SNAPSHOT.jar
```

**Результат:** Должен создаться файл `target/websocket-proxy-0.0.1-SNAPSHOT.jar`

### 3.5 Создание systemd service

```bash
nano /etc/systemd/system/websocket-proxy.service
```

**Содержимое файла:**

```ini
[Unit]
Description=WebSocket Proxy Server (Dixu v1)
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/websocket-proxy/dixu-v1-websocket-proxy
ExecStart=/usr/bin/java -jar /opt/websocket-proxy/dixu-v1-websocket-proxy/target/websocket-proxy-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

# Environment variables (если нужны)
Environment="JAVA_OPTS=-Xmx512m"

[Install]
WantedBy=multi-user.target
```

### 3.6 Запуск сервиса

```bash
# Перезагрузка конфигурации systemd
systemctl daemon-reload

# Включение автозапуска
systemctl enable websocket-proxy

# Запуск сервиса
systemctl start websocket-proxy

# Проверка статуса
systemctl status websocket-proxy

# Должно показать: Active: active (running)
```

### 3.7 Просмотр логов

```bash
# Просмотр последних 50 строк
journalctl -u websocket-proxy -n 50

# Мониторинг в реальном времени
journalctl -u websocket-proxy -f

# Для выхода из режима мониторинга нажмите Ctrl+C
```

### 3.8 Настройка firewall

```bash
# ВАЖНО: Сначала разрешите SSH!
ufw allow 22/tcp

# Разрешение WebSocket порта
ufw allow 8443/tcp

# Включение firewall
ufw --force enable

# Проверка правил
ufw status numbered
```

**Вывод должен показать:**

```
Status: active

To                         Action      From
--                         ------      ----
22/tcp                     ALLOW       Anywhere
8443/tcp                   ALLOW       Anywhere
```

### 3.9 Проверка работы сервера

```bash
# Проверка открытых портов
netstat -tlnp | grep 8443
# или
ss -tlnp | grep 8443

# Должно показать:
# tcp6  0  0 :::8443  :::*  LISTEN  <PID>/java
```

**Тест SSL соединения:**

```bash
openssl s_client -connect 144.31.248.91:8443 -showcerts
```

Если всё работает, увидите информацию о сертификате и строку:
```
Verify return code: 18 (self signed certificate)
```

Это нормально для самоподписанного сертификата.

---

## ЧАСТЬ 4: ПОЛУЧЕНИЕ ТОКЕНА АУТЕНТИФИКАЦИИ

### 4.1 Как работает система токенов

Перед подключением клиента необходимо получить **пару deviceId + token** с сервера. Эта система обеспечивает:
- Уникальную идентификацию каждого устройства
- Аутентификацию при подключении через WebSocket
- Предотвращение несанкционированного доступа

### 4.2 Генерация токена на сервере

**Алгоритм генерации** (из `DeviceTokenService.java`):

```java
private static String generateToken(String deviceId) {
    String secretKey = "goodSalt";  // Секретный ключ
    long timestamp = System.currentTimeMillis();
    String data = deviceId + secretKey + timestamp;
    
    // SHA-256 хеш
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(data.getBytes());
    
    // Base64 кодирование
    return Base64.getEncoder().encodeToString(hash);
}
```

**Формат deviceId:** `device-N` где N — автоинкрементный номер из базы данных.

### 4.3 REST API для получения токена

**Endpoint:** `GET /api/auth/token`

**URL:** `https://144.31.248.91:8443/api/auth/token`

**Метод 1: Через браузер**

Откройте в браузере:
```
https://144.31.248.91:8443/api/auth/token
```

Браузер покажет предупреждение о самоподписанном сертификате — примите риск и продолжите.

**Ответ (JSON):**
```json
{
  "deviceId": "device-1",
  "token": "Y+X3YH5k3XLst6NJpOQVXa9F6y80/hp9QQLwORykbXE="
}
```

**Метод 2: Через curl (игнорируя SSL)**

```bash
curl -k https://144.31.248.91:8443/api/auth/token
```

Флаг `-k` или `--insecure` игнорирует проверку самоподписанного сертификата.

**Метод 3: Через curl (с проверкой сертификата)**

```bash
curl --cacert /opt/websocket-proxy/certs/server-cert.crt \
     https://144.31.248.91:8443/api/auth/token
```

**Метод 4: Через PowerShell (Windows)**

```powershell
# Игнорирование SSL ошибок
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = {$true}

# Запрос
$response = Invoke-RestMethod -Uri "https://144.31.248.91:8443/api/auth/token" -Method Get

# Вывод
Write-Host "Device ID: $($response.deviceId)"
Write-Host "Token: $($response.token)"
```

### 4.4 Хранение токена в базе данных

После генерации токен сохраняется в SQLite базу данных:

**Таблица:** `device_tokens`

**Структура:**
```sql
CREATE TABLE device_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    deviceId TEXT NOT NULL UNIQUE,
    token TEXT NOT NULL
);
```

**Просмотр токенов в БД:**

```bash
# Подключение к базе данных
sqlite3 /opt/websocket-proxy/dixu-v1-websocket-proxy/mydb.sqlite

# Просмотр всех токенов
SELECT * FROM device_tokens;

# Поиск токена по deviceId
SELECT token FROM device_tokens WHERE deviceId = 'device-1';

# Выход
.quit
```

### 4.5 Проверка токена при подключении

При подключении WebSocket клиента сервер проверяет токен в `WebSocketProxyHandler.java`:

**Шаг 1:** Извлечение токена из запроса
- Из заголовка `Authorization: Bearer <token>`
- Или из URL параметра `?token=<token>`

**Шаг 2:** Валидация токена
```java
if (token != null && deviceTokenService.validateToken(deviceId, token)) {
    // Токен валиден, разрешаем подключение
    deviceSessionManager.addDeviceWithSession(deviceId, session);
} else {
    // Токен невалиден, закрываем соединение
    session.close(new CloseStatus(4001, "Authentication failed"));
}
```

**Коды ошибок:**
- **4000** — Устройство с таким deviceId уже подключено
- **4001** — Неверный токен (authentication failed)
- **4002** — Отсутствует deviceId в запросе

### 4.6 Пошаговая инструкция получения токена

**Шаг 1:** Убедитесь, что сервер запущен

```bash
systemctl status websocket-proxy
```

**Шаг 2:** Получите токен через curl

```bash
curl -k https://144.31.248.91:8443/api/auth/token | jq
```

**Вывод:**
```json
{
  "deviceId": "device-1",
  "token": "aB3cD4eF5gH6iJ7kL8mN9oP0qR1sT2uV3wX4yZ5=="
}
```

**Шаг 3:** Скопируйте полученные данные

Сохраните значения `deviceId` и `token`.

**Шаг 4:** Вставьте в конфигурацию клиента

Откройте `src/main/resources/config.properties` и обновите:

```properties
DEVICEID=device-1
AUTH_TOKEN=aB3cD4eF5gH6iJ7kL8mN9oP0qR1sT2uV3wX4yZ5==
```

### 4.7 Создание дополнительных токенов

Каждый раз при обращении к `/api/auth/token` создаётся **новая пара deviceId + token**.

**Пример: создание 5 токенов**

```bash
for i in {1..5}; do
    echo "=== Токен $i ==="
    curl -k https://144.31.248.91:8443/api/auth/token | jq
    echo ""
done
```

**Результат:**
```
=== Токен 1 ===
{"deviceId":"device-1","token":"xxx..."}

=== Токен 2 ===
{"deviceId":"device-2","token":"yyy..."}

=== Токен 3 ===
{"deviceId":"device-3","token":"zzz..."}
...
```

### 4.8 Управление токенами через SQLite

**Просмотр всех токенов:**

```bash
sqlite3 /opt/websocket-proxy/dixu-v1-websocket-proxy/mydb.sqlite \
  "SELECT id, deviceId, substr(token, 1, 20) || '...' as token FROM device_tokens;"
```

**Удаление токена:**

```bash
sqlite3 /opt/websocket-proxy/dixu-v1-websocket-proxy/mydb.sqlite \
  "DELETE FROM device_tokens WHERE deviceId = 'device-1';"
```

**Обновление токена (не рекомендуется, лучше создать новый):**

```bash
sqlite3 /opt/websocket-proxy/dixu-v1-websocket-proxy/mydb.sqlite \
  "UPDATE device_tokens SET token = 'NEW_TOKEN' WHERE deviceId = 'device-1';"
```

### 4.9 Автоматизация получения токена

**Скрипт для автоматического получения и сохранения токена:**

```bash
#!/bin/bash

# Получаем токен с сервера
RESPONSE=$(curl -k -s https://144.31.248.91:8443/api/auth/token)

# Парсим JSON
DEVICE_ID=$(echo $RESPONSE | jq -r '.deviceId')
TOKEN=$(echo $RESPONSE | jq -r '.token')

echo "Полученные данные:"
echo "Device ID: $DEVICE_ID"
echo "Token: $TOKEN"

# Обновляем config.properties
CONFIG_FILE="src/main/resources/config.properties"

# Создаём резервную копию
cp $CONFIG_FILE ${CONFIG_FILE}.backup

# Обновляем значения
sed -i "s/^DEVICEID=.*/DEVICEID=$DEVICE_ID/" $CONFIG_FILE
sed -i "s/^AUTH_TOKEN=.*/AUTH_TOKEN=$TOKEN/" $CONFIG_FILE

echo ""
echo "✅ Конфигурация обновлена!"
echo "Файл: $CONFIG_FILE"
```

**Использование:**

```bash
chmod +x get-token.sh
./get-token.sh
```

### 4.10 Важные замечания

⚠️ **Безопасность:**
- Токен передаётся в открытом виде по HTTPS (защищён SSL)
- Секретный ключ `"goodSalt"` захардкожен в коде — для продакшена измените его!
- Храните токены в безопасном месте

⚠️ **Один deviceId = одно подключение:**
- Одно устройство с данным deviceId может быть подключено только один раз
- При попытке второго подключения первое будет отклонено с кодом 4000

⚠️ **Токены не истекают:**
- В текущей реализации токены действительны бесконечно
- Для продакшена рекомендуется добавить TTL (время жизни токена)

---

## ЧАСТЬ 5: РАЗВЕРТЫВАНИЕ КЛИЕНТСКОГО ПРИЛОЖЕНИЯ

### 5.1 Клонирование репозитория клиента

**На клиентской машине** (или на том же сервере для теста):

```bash
cd /opt
git clone https://github.com/ramanzes/dixu-v1-websocket-client.git
cd dixu-v1-websocket-client
git checkout dev4
```

### 5.2 Получение токена для клиента

**ВАЖНО:** Перед настройкой клиента получите токен с сервера!

```bash
# Получение токена
curl -k https://144.31.248.91:8443/api/auth/token

# Сохраните полученные deviceId и token
```

### 5.3 Настройка конфигурации клиента

Отредактируйте `src/main/resources/config.properties`:

```bash
nano src/main/resources/config.properties
```

**Обновите следующие параметры:**

```properties
# === ВАЖНО: Используйте deviceId и token, полученные от сервера! ===
# Получить можно через: curl -k https://144.31.248.91:8443/api/auth/token

# Уникальный ID устройства (ПОЛУЧЕННЫЙ ОТ СЕРВЕРА!)
DEVICEID=device-1

# Локальный веб-сервер на этом устройстве (если есть)
LOCALWEBPORT=5000
LOCALWEBPORTSSL=443
LOCALHOSTURL=localhost
LOCALHOMEURL=

# === НАСТРОЙКИ ПОДКЛЮЧЕНИЯ К СЕРВЕРУ ===
# IP адрес сервера WebSocket Proxy
IPWS=144.31.248.91

# Порт сервера
PORTWS=8443

# Токен аутентификации (ПОЛУЧЕННЫЙ ОТ СЕРВЕРА!)
AUTH_TOKEN=Y+X3YH5k3XLst6NJpOQVXa9F6y80/hp9QQLwORykbXE=

# Отладка
DEBUG=true
```

**Важные моменты:**
- `DEVICEID` и `AUTH_TOKEN` должны быть получены с сервера через `/api/auth/token`
- Не используйте один и тот же deviceId на разных клиентах!
- `IPWS` = IP адрес вашего сервера (144.31.248.91)
- `PORTWS` = 8443

### 5.4 Копирование truststore (опционально)

Если вы хотите использовать проверку сертификата (а не `disableSSLCertificateChecking`):

```bash
# Скопируйте с сервера
scp root@144.31.248.91:/opt/websocket-proxy/certs/client-truststore.jks /opt/dixu-v1-websocket-client/

# Или создайте локально из server-cert.crt
```

### 5.5 Сборка клиента

```bash
cd /opt/dixu-v1-websocket-client

# Сборка
mvn clean package

# Проверка
ls -lh target/TestWebSocketClient-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### 5.6 Запуск клиента

**Базовый запуск (с отключенной проверкой сертификата):**

```bash
java -jar target/TestWebSocketClient-1.0-SNAPSHOT-jar-with-dependencies.jar
```

**Запуск с проверкой сертификата через truststore:**

```bash
java -Djavax.net.ssl.trustStore=/opt/dixu-v1-websocket-client/client-truststore.jks \
     -Djavax.net.ssl.trustStorePassword=changeit \
     -jar target/TestWebSocketClient-1.0-SNAPSHOT-jar-with-dependencies.jar
```

**Запуск с дополнительной отладкой SSL:**

```bash
java -Djavax.net.debug=ssl,handshake \
     -jar target/TestWebSocketClient-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### 5.7 Проверка подключения

При успешном подключении вы должны увидеть в логах:

```
Connection established to wss://144.31.248.91:8443/ws?deviceId=device-test-01&token=...
WebSocket session opened
```

На сервере в логах:

```bash
journalctl -u websocket-proxy -f

# Должно показать:
# WebSocket connection established for device: device-test-01
```

---

## ЧАСТЬ 6: ПОНИМАНИЕ АРХИТЕКТУРЫ

### 6.1 Как работает аутентификация

**Клиент отправляет:**
1. `deviceId` в URL параметрах: `?deviceId=device-1`
2. `token` в URL параметрах: `&token=Y+X3YH5k3XLst6NJpOQVXa9F6y80/hp9QQLwORykbXE=`
3. Дополнительные заголовки через `CustomConfigurator`:
   - `X-Device-Id`: ID устройства
   - `Authorization: Bearer <token>`

**Сервер проверяет:**
1. Наличие `deviceId` в запросе
2. Токен через метод `deviceTokenService.validateToken(deviceId, token)`
3. Не подключено ли уже устройство с таким ID
4. При успехе: регистрирует сессию в `DeviceSessionManager`
5. При ошибке отправляет специальные коды закрытия:
   - `4000` - устройство уже подключено
   - `4001` - ошибка аутентификации (неверный токен)
   - `4002` - отсутствует deviceId

**Валидация токена** (из `DeviceTokenService.java`):
```java
public boolean validateToken(String deviceId, String token) {
    return getTokenByDeviceId(deviceId).equals(token);
}
```

Сервер сравнивает переданный токен с токеном, сохранённым в базе данных для данного deviceId.

### 6.2 Endpoint и маршрутизация

**WebSocket endpoint:** `/ws`

Регистрируется в `WebSocketConfig.java`:

```java
registry.addHandler(webSocketProxyHandler, "/ws")
    .setAllowedOrigins("*");
```

**Полный URL подключения:**
```
wss://144.31.248.91:8443/ws?deviceId=device-1&token=YOUR_TOKEN
```

**REST API для получения токена:**
```
https://144.31.248.91:8443/api/auth/token
```

### 6.3 База данных

Сервер использует **SQLite** для хранения:
- Токенов устройств (`DeviceToken`)
- Информации об устройствах (`Devices`)
- Сессий пользователей (`UserSession`)

**Файл БД:** `./mydb.sqlite` (создаётся автоматически при первом запуске)

**Расположение:** `/opt/websocket-proxy/dixu-v1-websocket-proxy/mydb.sqlite`

### 6.4 Как работает SSL в клиенте

В `WebSocketLayer.java` есть метод `disableSSLCertificateChecking()`, который **отключает проверку SSL сертификатов**:

```java
public void disableSSLCertificateChecking() {
    TrustManager[] trustAllCerts = new TrustManager[] {
        new X509TrustManager() {
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
                // Принимает любой сертификат
            }
        }
    };
    SSLContext sc = SSLContext.getInstance("TLS");
    sc.init(null, trustAllCerts, new java.security.SecureRandom());
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
}
```

**Это нормально для разработки**, но для продакшена следует:
- Использовать реальный сертификат от CA
- Или настроить truststore с вашим сертификатом
- Удалить вызов `disableSSLCertificateChecking()`

---

## ЧАСТЬ 7: УПРАВЛЕНИЕ СЕРВИСОМ

### 7.1 Основные команды

```bash
# Запуск
systemctl start websocket-proxy

# Остановка
systemctl stop websocket-proxy

# Перезапуск
systemctl restart websocket-proxy

# Статус
systemctl status websocket-proxy

# Логи (последние 100 строк)
journalctl -u websocket-proxy -n 100

# Логи в реальном времени
journalctl -u websocket-proxy -f

# Отключение автозапуска
systemctl disable websocket-proxy

# Включение автозапуска
systemctl enable websocket-proxy
```

### 7.2 Изменение конфигурации

После изменения `application.properties`:

```bash
# 1. Пересборка проекта
cd /opt/websocket-proxy/dixu-v1-websocket-proxy
mvn clean package -DskipTests

# 2. Перезапуск сервиса
systemctl restart websocket-proxy

# 3. Проверка
systemctl status websocket-proxy
journalctl -u websocket-proxy -n 50
```

### 7.3 Обновление кода из Git

```bash
cd /opt/websocket-proxy/dixu-v1-websocket-proxy

# Остановка сервиса
systemctl stop websocket-proxy

# Обновление кода
git pull origin dev4

# Пересборка
mvn clean package -DskipTests

# Запуск
systemctl start websocket-proxy
```

---

## ЧАСТЬ 8: УСТРАНЕНИЕ НЕПОЛАДОК

### 8.1 Сервер не запускается

**Проверка 1: Логи**
```bash
journalctl -u websocket-proxy -n 100 --no-pager
```

**Проверка 2: Порт занят**
```bash
lsof -i :8443
# Если порт занят другим процессом, остановите его
```

**Проверка 3: Keystore**
```bash
ls -lh /opt/websocket-proxy/dixu-v1-websocket-proxy/src/main/resources/keystore.p12

# Если файла нет, скопируйте снова
cp /opt/websocket-proxy/certs/keystore.p12 /opt/websocket-proxy/dixu-v1-websocket-proxy/src/main/resources/
```

**Проверка 4: Права доступа**
```bash
# Убедитесь, что jar файл исполняемый
chmod +x /opt/websocket-proxy/dixu-v1-websocket-proxy/target/websocket-proxy-0.0.1-SNAPSHOT.jar
```

### 8.2 Клиент не может подключиться

**Ошибка: Connection refused**

```bash
# На сервере проверьте, что сервис запущен
systemctl status websocket-proxy

# Проверьте открытые порты
netstat -tlnp | grep 8443

# Проверьте firewall
ufw status
```

**Ошибка: SSL handshake failed / No subject alternative names present**

Это самая частая ошибка! Сертификат не содержит SAN (Subject Alternative Name).

**Решение:**
```bash
# Пересоздайте сертификат с SAN
cd /opt/websocket-proxy/certs
rm keystore.p12

keytool -genkeypair \
  -alias tomcat \
  -keyalg RSA \
  -keysize 2048 \
  -validity 3650 \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -storepass DfdyaIKbJl21FWt7Fq8 \
  -dname "CN=ВАШ_IP, OU=WebSocket, O=Dixu, L=Moscow, ST=Moscow, C=RU" \
  -ext "SAN=IP:ВАШ_IP"

# Экспорт и создание truststore
keytool -exportcert -alias tomcat -keystore keystore.p12 \
  -storetype PKCS12 -storepass DfdyaIKbJl21FWt7Fq8 -file server-cert.crt

rm client-truststore.jks
keytool -importcert -alias websocket-server -file server-cert.crt \
  -keystore client-truststore.jks -storepass changeit -noprompt

# Копирование и перезапуск
cp keystore.p12 /opt/websocket-proxy/dixu-v1-websocket-proxy/src/main/resources/
systemctl restart websocket-proxy
```

**Альтернатива (для тестирования):** Отключите проверку SSL в клиенте

В `WebSocketLayer.java` добавьте вызов в начало метода `connect()`:
```java
public void connect() {
    try {
        disableSSLCertificateChecking(); // Добавьте эту строку!
        
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        // ... остальной код
```

Затем пересоберите:
```bash
cd /opt/dixu-v1-websocket-client
mvn clean package
```

**Ошибка: Close code 4001 (Authentication failed)**

Неверный токен. 

**Решение:**
```bash
# Получите новый токен
curl -k https://144.31.248.91:8443/api/auth/token

# Обновите config.properties с новыми deviceId и token
```

**Ошибка: Close code 4000 (Device already connected)**

Это означает, что устройство с таким `deviceId` уже подключено.

**Решение:**
```bash
# Получите новый токен (с новым deviceId)
curl -k https://144.31.248.91:8443/api/auth/token

# Обновите config.properties
```

Или отключите предыдущее соединение.

**Ошибка: Certificate hostname verification failed**

Это происходит, если CN в сертификате не совпадает с адресом подключения.

**Решение:**
- В сертификате должно быть `CN=144.31.248.91`
- В клиенте `IPWS=144.31.248.91`
- Или используйте `disableSSLCertificateChecking()` (уже есть в коде)

### 8.3 Проблемы с базой данных

**Ошибка: Could not open SQLite database**

```bash
# Проверьте права доступа
ls -lh /opt/websocket-proxy/dixu-v1-websocket-proxy/mydb.sqlite

# Создайте файл вручную, если нужно
touch /opt/websocket-proxy/dixu-v1-websocket-proxy/mydb.sqlite
chmod 666 /opt/websocket-proxy/dixu-v1-websocket-proxy/mydb.sqlite
```

**Сброс базы данных:**

```bash
cd /opt/websocket-proxy/dixu-v1-websocket-proxy
systemctl stop websocket-proxy

# Удаление БД (ВНИМАНИЕ: потеряются все данные!)
rm mydb.sqlite

# При следующем запуске БД создастся заново
systemctl start websocket-proxy
```

### 8.4 Отладка SSL

**Включение подробных логов SSL на сервере:**

Измените systemd service:

```bash
nano /etc/systemd/system/websocket-proxy.service
```

Добавьте в `ExecStart`:

```ini
ExecStart=/usr/bin/java -Djavax.net.debug=ssl,handshake -jar /opt/websocket-proxy/dixu-v1-websocket-proxy/target/websocket-proxy-0.0.1-SNAPSHOT.jar
```

Перезапустите:

```bash
systemctl daemon-reload
systemctl restart websocket-proxy
journalctl -u websocket-proxy -f
```

**Включение отладки на клиенте:**

```bash
java -Djavax.net.debug=ssl,handshake \
     -jar target/TestWebSocketClient-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### 8.5 Не могу получить токен через /api/auth/token

**Проверка 1: Сервер запущен**
```bash
systemctl status websocket-proxy
```

**Проверка 2: Порт открыт**
```bash
netstat -tlnp | grep 8443
ufw status
```

**Проверка 3: SSL работает**
```bash
openssl s_client -connect 144.31.248.91:8443
```

**Проверка 4: Endpoint доступен**
```bash
# С игнорированием SSL
curl -k -v https://144.31.248.91:8443/api/auth/token

# Проверка логов сервера
journalctl -u websocket-proxy -f
```

---

## ЧАСТЬ 9: БЫСТРЫЙ СТАРТ (все команды)

### 9.1 На сервере (144.31.248.91)

```bash
#!/bin/bash

# 1. ПОДГОТОВКА
apt update && apt upgrade -y
apt install -y openjdk-21-jdk maven git ufw

# 2. СОЗДАНИЕ СЕРТИФИКАТОВ
mkdir -p /opt/websocket-proxy/certs && cd /opt/websocket-proxy/certs
keytool -genkeypair \
  -alias tomcat \
  -keyalg RSA \
  -keysize 2048 \
  -validity 3650 \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -storepass DfdyaIKbJl21FWt7Fq8 \
  -dname "CN=144.31.248.91, OU=WebSocket, O=Dixu, L=Moscow, ST=Moscow, C=RU"

keytool -exportcert \
  -alias tomcat \
  -keystore keystore.p12 \
  -storetype PKCS12 \
  -storepass DfdyaIKbJl21FWt7Fq8 \
  -file server-cert.crt

# 3. КЛОНИРОВАНИЕ И СБОРКА
cd /opt/websocket-proxy
git clone https://github.com/ramanzes/dixu-v1-websocket-proxy.git
cd dixu-v1-websocket-proxy
git checkout dev4

# Копирование keystore
cp /opt/websocket-proxy/certs/keystore.p12 src/main/resources/

# ВАЖНО: Отредактируйте application.properties
nano src/main/resources/application.properties
# Измените server.address=192.168.0.19 на server.address=0.0.0.0

# Сборка
mvn clean package -DskipTests

# 4. СОЗДАНИЕ SYSTEMD SERVICE
cat > /etc/systemd/system/websocket-proxy.service <<'EOF'
[Unit]
Description=WebSocket Proxy Server (Dixu v1)
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/websocket-proxy/dixu-v1-websocket-proxy
ExecStart=/usr/bin/java -jar /opt/websocket-proxy/dixu-v1-websocket-proxy/target/websocket-proxy-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

# 5. ЗАПУСК
systemctl daemon-reload
systemctl enable websocket-proxy
systemctl start websocket-proxy

# 6. FIREWALL
ufw allow 22/tcp
ufw allow 8443/tcp
ufw --force enable

# 7. ПРОВЕРКА
echo "=== STATUS ==="
systemctl status websocket-proxy
echo ""
echo "=== PORTS ==="
netstat -tlnp | grep 8443
echo ""
echo "=== LOGS (last 20 lines) ==="
journalctl -u websocket-proxy -n 20
```

### 9.2 На клиенте

```bash
#!/bin/bash

# 1. КЛОНИРОВАНИЕ
cd /opt
git clone https://github.com/ramanzes/dixu-v1-websocket-client.git
cd dixu-v1-websocket-client
git checkout dev4

# 2. ПОЛУЧЕНИЕ ТОКЕНА С СЕРВЕРА
echo "=== Получение токена с сервера ==="
RESPONSE=$(curl -k -s https://144.31.248.91:8443/api/auth/token)
DEVICE_ID=$(echo $RESPONSE | jq -r '.deviceId')
TOKEN=$(echo $RESPONSE | jq -r '.token')

echo "Device ID: $DEVICE_ID"
echo "Token: $TOKEN"

# 3. АВТОМАТИЧЕСКАЯ НАСТРОЙКА
cat > src/main/resources/config.properties <<EOF
DEVICEID=$DEVICE_ID
LOCALWEBPORT=5000
LOCALWEBPORTSSL=443
LOCALHOSTURL=localhost
LOCALHOMEURL=
IPWS=144.31.248.91
PORTWS=8443
DEBUG=true
AUTH_TOKEN=$TOKEN
EOF

echo "✅ Конфигурация создана"

# 4. СБОРКА
mvn clean package

# 5. ЗАПУСК
java -jar target/TestWebSocketClient-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## ЧАСТЬ 10: ПРОДАКШЕН РЕКОМЕНДАЦИИ

### 10.1 Безопасность

**1. Смените секретный ключ генерации токенов:**

В `DeviceTokenService.java` измените:

```java
private static String generateToken(String deviceId) {
    String secretKey = "ВАШТСИЛЬНЫЙСЕКРЕТНЫЙКЛЮЧ";  // ⚠️ ОБЯЗАТЕЛЬНО ИЗМЕНИТЕ!
    // ...
}
```

Пересоберите проект после изменения.

**2. Смените пароли keystore:**

```bash
# Создайте новый keystore с надёжным паролем
keytool -genkeypair \
  -alias tomcat \
  -keyalg RSA \
  -keysize 4096 \
  -validity 730 \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -storepass "ВАШ_СЛОЖНЫЙ_ПАРОЛЬ" \
  -dname "CN=144.31.248.91, OU=WebSocket, O=Dixu, L=Moscow, ST=Moscow, C=RU"

# Обновите application.properties
server.ssl.key-store-password=ВАШ_СЛОЖНЫЙ_ПАРОЛЬ
```

**3. Ограничьте доступ к /api/auth/token:**

Добавьте аутентификацию для endpoint'а получения токенов. В продакшене токены должны выдаваться только авторизованным пользователям.

**4. Ограничьте доступ к файлам:**

```bash
chmod 600 /opt/websocket-proxy/certs/*.p12
chmod 600 /opt/websocket-proxy/dixu-v1-websocket-proxy/src/main/resources/keystore.p12
chown root:root /opt/websocket-proxy/certs/*
```

**3. Настройте firewall более строго:**

```bash
# Разрешите только с определённых IP
ufw delete allow 8443/tcp
ufw allow from YOUR_CLIENT_IP to any port 8443
```

**4. Создайте отдельного пользователя (не root):**

```bash
useradd -r -s /bin/false websocket-proxy
chown -R websocket-proxy:websocket-proxy /opt/websocket-proxy

# Обновите systemd service
nano /etc/systemd/system/websocket-proxy.service
# Измените User=root на User=websocket-proxy
```

**5. Добавьте TTL для токенов:**

В продакшене токены не должны жить вечно. Добавьте в `DeviceToken`:

```java
@Column
private LocalDateTime createdAt;

@Column
private LocalDateTime expiresAt;
```

### 10.2 Мониторинг

**Установка мониторинга:**

```bash
apt install -y prometheus-node-exporter

# Логирование в файл
nano /etc/systemd/system/websocket-proxy.service
```

Добавьте:

```ini
StandardOutput=append:/var/log/websocket-proxy.log
StandardError=append:/var/log/websocket-proxy-error.log
```

**Ротация логов:**

```bash
cat > /etc/logrotate.d/websocket-proxy <<'EOF'
/var/log/websocket-proxy*.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
}
EOF
```

### 10.3 Резервное копирование

```bash
# Скрипт backup
cat > /root/backup-websocket-proxy.sh <<'EOF'
#!/bin/bash
BACKUP_DIR="/backup/websocket-proxy"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $BACKUP_DIR

# Backup базы данных
cp /opt/websocket-proxy/dixu-v1-websocket-proxy/mydb.sqlite \
   $BACKUP_DIR/mydb_$DATE.sqlite

# Backup конфигурации
cp /opt/websocket-proxy/dixu-v1-websocket-proxy/src/main/resources/application.properties \
   $BACKUP_DIR/application_$DATE.properties

# Backup сертификатов
cp /opt/websocket-proxy/certs/keystore.p12 \
   $BACKUP_DIR/keystore_$DATE.p12

# Удаление старых backup (старше 30 дней)
find $BACKUP_DIR -type f -mtime +30 -delete

echo "Backup completed: $DATE"
EOF

chmod +x /root/backup-websocket-proxy.sh

# Добавление в cron (ежедневно в 2:00)
crontab -e
# Добавьте строку:
# 0 2 * * * /root/backup-websocket-proxy.sh
```

### 10.4 Использование реального SSL сертификата

Для продакшена рекомендуется использовать сертификат от Let's Encrypt:

```bash
# Установка certbot
apt install -y certbot

# Получение сертификата (требуется домен)
certbot certonly --standalone -d your-domain.com

# Конвертация в PKCS12
openssl pkcs12 -export \
  -in /etc/letsencrypt/live/your-domain.com/fullchain.pem \
  -inkey /etc/letsencrypt/live/your-domain.com/privkey.pem \
  -out keystore.p12 \
  -name tomcat \
  -passout pass:YOUR_PASSWORD

# Копирование в проект
cp keystore.p12 /opt/websocket-proxy/dixu-v1-websocket-proxy/src/main/resources/

# Обновление application.properties
nano /opt/websocket-proxy/dixu-v1-websocket-proxy/src/main/resources/application.properties
# server.ssl.key-store-password=YOUR_PASSWORD

# Пересборка и перезапуск
cd /opt/websocket-proxy/dixu-v1-websocket-proxy
mvn clean package -DskipTests
systemctl restart websocket-proxy
```

---

## ЧАСТЬ 11: СПРАВОЧНАЯ ИНФОРМАЦИЯ

### 11.1 Структура проекта сервера

```
dixu-v1-websocket-proxy/
├── src/main/
│   ├── java/com/websocketproxy/
│   │   ├── config/
│   │   │   └── WebSocketConfig.java          # Конфигурация WebSocket
│   │   ├── controller/
│   │   │   ├── ProxyController.java          # REST контроллеры
│   │   │   ├── RootController.java
│   │   │   └── TokenController.java
│   │   ├── repository/
│   │   │   ├── DeviceTokenRepository.java    # JPA репозитории
│   │   │   ├── Devices.java
│   │   │   ├── UserSession.java
│   │   │   └── UsersSessionManager.java
│   │   ├── services/
│   │   │   ├── DeviceSessionManager.java     # Бизнес-логика
│   │   │   ├── DeviceTokenService.java
│   │   │   ├── HttpRequest.java
│   │   │   ├── HttpResponse.java
│   │   │   └── MyWebsocketUtils.java
│   │   ├── websocket/
│   │   │   └── WebSocketProxyHandler.java    # Обработчик WebSocket
│   │   └── WebSocketProxyApplication.java    # Главный класс
│   └── resources/
│       ├── application.properties             # Конфигурация
│       └── keystore.p12                       # SSL сертификат
├── mydb.sqlite                                # База данных
├── pom.xml                                    # Maven конфигурация
└── target/
    └── websocket-proxy-0.0.1-SNAPSHOT.jar    # Собранный JAR
```

### 11.2 Структура проекта клиента

```
dixu-v1-websocket-client/
├── src/main/
│   ├── java/
│   │   ├── config/
│   │   │   └── MyConfig.java                 # Конфигурация
│   │   ├── repository/
│   │   │   ├── ThisDevice.java
│   │   │   ├── UserSession.java
│   │   │   └── UsersSessionManager.java
│   │   ├── services/
│   │   │   ├── BusinessLogicLayer.java       # Бизнес-логика
│   │   │   ├── HttpRequest.java
│   │   │   └── HttpResponse.java
│   │   ├── utils/
│   │   │   ├── HttpUtils.java
│   │   │   └── UtilsLayer.java
│   │   └── websocket/
│   │       ├── WebSocketLayer.java           # Главный класс WebSocket
│   │       └── WebSocketInputStream.java
│   └── resources/
│       └── config.properties                  # Конфигурация клиента
├── pom.xml
└── target/
    └── TestWebSocketClient-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### 11.3 Важные параметры

**Сервер (application.properties):**
```properties
server.address=0.0.0.0                        # Слушать на всех интерфейсах
server.port=8443                              # HTTPS/WSS порт
server.ssl.key-store=classpath:keystore.p12   # Путь к keystore
server.ssl.key-store-password=...             # Пароль keystore
server.ssl.keyStoreType=PKCS12                # Тип keystore
server.ssl.keyAlias=tomcat                    # Алиас ключа
BUFFER_SIZE=4096                              # Размер буфера
DEBUG=true                                    # Режим отладки
spring.datasource.url=jdbc:sqlite:file:./mydb.sqlite  # БД
```

**Клиент (config.properties):**
```properties
DEVICEID=device-1                             # ID устройства (от сервера!)
IPWS=144.31.248.91                            # IP сервера
PORTWS=8443                                   # Порт сервера
AUTH_TOKEN=...                                # Токен (от сервера!)
DEBUG=true                                    # Режим отладки
LOCALWEBPORT=5000                             # Порт локального веб-сервера
```

### 11.4 API Endpoints

**Получение токена:**
- **URL:** `GET /api/auth/token`
- **Ответ:** `{"deviceId": "device-N", "token": "BASE64_STRING"}`
- **Пример:** `curl -k https://144.31.248.91:8443/api/auth/token`

**WebSocket подключение:**
- **URL:** `wss://144.31.248.91:8443/ws?deviceId=DEVICE_ID&token=TOKEN`
- **Протокол:** WSS (WebSocket Secure)
- **Аутентификация:** deviceId + token

### 11.5 Коды закрытия WebSocket

Сервер использует специальные коды закрытия:

- **4000** - Устройство уже подключено
- **4001** - Ошибка аутентификации (неверный токен)
- **4002** - Отсутствует deviceId
- **1000** - Нормальное закрытие
- **1008** - Нарушение политики

### 11.6 Полезные ссылки

- **Spring Boot WebSocket**: https://spring.io/guides/gs/messaging-stomp-websocket/
- **Java WebSocket API**: https://docs.oracle.com/javaee/7/api/javax/websocket/package-summary.html
- **Keytool документация**: https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html
- **Let's Encrypt**: https://letsencrypt.org/

---

## ЗАКЛЮЧЕНИЕ

Теперь у вас есть полная инструкция по развертыванию WebSocket Proxy с SSL/TLS. Основные шаги:

1. ✅ Создан самоподписанный PKCS12 сертификат
2. ✅ Развёрнут серверный WebSocket Proxy на Spring Boot
3. ✅ Настроен клиент на Java с WebSocket
4. ✅ Настроен firewall и systemd service
5. ✅ Понятна архитектура и аутентификация

**Для продакшена не забудьте:**
- Заменить самоподписанный сертификат на реальный от CA
- Сменить все пароли
- Настроить мониторинг и backup
- Ограничить доступ через firewall
- Запустить от непривилегированного пользователя

Если возникнут проблемы, обращайтесь к разделу **ЧАСТЬ 7: УСТРАНЕНИЕ НЕПОЛАДОК**.
