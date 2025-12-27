### Из серверного кода

1. **Artifact ID**: `websocket-proxy` версия `0.0.1-SNAPSHOT`
2. **Spring Boot**: версия 3.4.1
3. **Java**: 21
4. **Endpoint**: `/ws` (регистрируется в `WebSocketConfig.java`)
5. **SSL параметры** из реального `application.properties`:
   - Keystore: `classpath:keystore.p12`
   - Alias: `tomcat`
   - Type: `PKCS12`
   - Password: `DfdyaIKbJl21FWt7Fq8`
6. **База данных**: SQLite (`mydb.sqlite`)
7. **Важно**: `server.address=192.168.0.19` нужно изменить на `0.0.0.0`

### Из клиентского кода

1. **Artifact ID**: `TestWebSocketClient` версия `1.0-SNAPSHOT`
2. **Java**: 11+ (компилятор настроен на 11)
3. **Main класс**: `websocket.WebSocketLayer`
4. **URL формат**: `wss://IPWS:PORTWS/ws?deviceId=DEVICEID&token=AUTH_TOKEN`
5. **SSL**: Клиент использует `disableSSLCertificateChecking()` - принимает любой сертификат
6. **Аутентификация**:
   - `deviceId` в URL параметрах
   - `token` в URL параметрах
   - Заголовки `X-Device-Id` и `Authorization: Bearer`
7. **Коды закрытия**:
   - 4000 = устройство уже подключено
   - 4001 = ошибка аутентификации
   - 4002 = отсутствует deviceId

### Структура инструкции

1. ✅ Подготовка сервера (Debian 13, Java 21, Maven)
2. ✅ Создание PKCS12 сертификата с правильными параметрами
3. ✅ Развертывание сервера с правильными путями
4. ✅ Развертывание клиента с правильной конфигурацией
5. ✅ Объяснение архитектуры и аутентификации
6. ✅ Управление через systemd
7. ✅ Устранение неполадок
8. ✅ Быстрый старт с готовыми скриптами
9. ✅ Продакшен рекомендации
10. ✅ Справочная информация

Теперь у тебя есть всё для развертывания! Если что-то непонятно или возникнут вопросы в процессе - пиши, помогу разобраться.

# Полная инструкция по развертыванию WebSocket Proxy с самоподписанным сертификатом

## Информация о проекте

**Серверная часть:** <https://github.com/ramanzes/dixu-v1-websocket-proxy> (ветка dev4)  
**Клиентская часть:** <https://github.com/ramanzes/dixu-v1-websocket-client> (ветка dev4)

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

### 2.2 Генерация PKCS12 keystore для сервера

**Важно:** Параметры взяты из реального `application.properties`:

```bash
keytool -genkeypair \
  -alias tomcat \
  -keyalg RSA \
  -keysize 2048 \
  -validity 3650 \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -storepass DfdyaIKbJl21FWt7Fq8 \
  -dname "CN=144.31.248.91, OU=WebSocket, O=Dixu, L=Moscow, ST=Moscow, C=RU"
```

**Что здесь используется:**

- `-alias tomcat` - из `server.ssl.keyAlias=tomcat`
- `-storetype PKCS12` - из `server.ssl.keyStoreType=PKCS12`
- `-keystore keystore.p12` - из `server.ssl.key-store=classpath:keystore.p12`
- `-storepass DfdyaIKbJl21FWt7Fq8` - из `server.ssl.key-store-password`
- `CN=144.31.248.91` - IP адрес сервера в качестве Common Name

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

## ЧАСТЬ 4: РАЗВЕРТЫВАНИЕ КЛИЕНТСКОГО ПРИЛОЖЕНИЯ

### 4.1 Клонирование репозитория клиента

**На клиентской машине** (или на том же сервере для теста):

```bash
cd /opt
git clone https://github.com/ramanzes/dixu-v1-websocket-client.git
cd dixu-v1-websocket-client
git checkout dev4
```

### 4.2 Настройка конфигурации клиента

Отредактируйте `src/main/resources/config.properties`:

```bash
nano src/main/resources/config.properties
```

**Обновите следующие параметры:**

```properties
# Уникальный ID устройства (измените на свой)
DEVICEID=device-test-01

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

# Токен аутентификации
# ВАЖНО: Используйте реальный токен из вашей системы
AUTH_TOKEN=Y+X3YH5k3XLst6NJpOQVXa9F6y80/hp9QQLwORykbXE=

# Отладка
DEBUG=true
```

**Важные моменты:**

- `DEVICEID` должен быть уникальным для каждого клиента
- `IPWS` = IP адрес вашего сервера (144.31.248.91)
- `PORTWS` = 8443
- `AUTH_TOKEN` - используется для аутентификации, должен совпадать с сервером

### 4.3 Копирование truststore (опционально)

Если вы хотите использовать проверку сертификата (а не `disableSSLCertificateChecking`):

```bash
# Скопируйте с сервера
scp root@144.31.248.91:/opt/websocket-proxy/certs/client-truststore.jks /opt/dixu-v1-websocket-client/

# Или создайте локально из server-cert.crt
```

### 4.4 Сборка клиента

```bash
cd /opt/dixu-v1-websocket-client

# Сборка
mvn clean package

# Проверка
ls -lh target/TestWebSocketClient-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### 4.5 Запуск клиента

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

### 4.6 Проверка подключения

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

## ЧАСТЬ 5: ПОНИМАНИЕ АРХИТЕКТУРЫ

### 5.1 Как работает аутентификация

**Клиент отправляет:**

1. `deviceId` в URL параметрах: `?deviceId=device-test-01`
2. `token` в URL параметрах: `&token=Y+X3YH5k3XLst6NJpOQVXa9F6y80/hp9QQLwORykbXE=`
3. Дополнительные заголовки через `CustomConfigurator`:
   - `X-Device-Id`: ID устройства
   - `Authorization: Bearer <token>`

**Сервер проверяет:**

- В `WebSocketProxyHandler.afterConnectionEstablished()` проверяется наличие `deviceId`
- Проверяется, не подключено ли уже устройство с таким ID
- При ошибке отправляются специальные коды закрытия:
  - `4000` - устройство уже подключено
  - `4001` - ошибка аутентификации
  - `4002` - отсутствует deviceId

### 5.2 Endpoint и маршрутизация

**WebSocket endpoint:** `/ws`

Регистрируется в `WebSocketConfig.java`:

```java
registry.addHandler(webSocketProxyHandler, "/ws")
    .setAllowedOrigins("*");
```

**Полный URL подключения:**

```
wss://144.31.248.91:8443/ws?deviceId=YOUR_DEVICE_ID&token=YOUR_TOKEN
```

### 5.3 База данных

Сервер использует **SQLite** для хранения:

- Токенов устройств (`DeviceToken`)
- Информации об устройствах (`Devices`)
- Сессий пользователей (`UserSession`)

**Файл БД:** `./mydb.sqlite` (создаётся автоматически при первом запуске)

**Расположение:** `/opt/websocket-proxy/dixu-v1-websocket-proxy/mydb.sqlite`

### 5.4 Как работает SSL в клиенте

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

## ЧАСТЬ 6: УПРАВЛЕНИЕ СЕРВИСОМ

### 6.1 Основные команды

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

### 6.2 Изменение конфигурации

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

### 6.3 Обновление кода из Git

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

## ЧАСТЬ 7: УСТРАНЕНИЕ НЕПОЛАДОК

### 7.1 Сервер не запускается

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

### 7.2 Клиент не может подключиться

**Ошибка: Connection refused**

```bash
# На сервере проверьте, что сервис запущен
systemctl status websocket-proxy

# Проверьте открытые порты
netstat -tlnp | grep 8443

# Проверьте firewall
ufw status
```

**Ошибка: SSL handshake failed**

```bash
# Проверьте сертификат на сервере
openssl s_client -connect 144.31.248.91:8443 -showcerts

# Убедитесь, что в application.properties правильно указан keystore
cat /opt/websocket-proxy/dixu-v1-websocket-proxy/src/main/resources/application.properties | grep ssl
```

**Ошибка: Certificate hostname verification failed**

Это происходит, если CN в сертификате не совпадает с адресом подключения.

**Решение:**

- В сертификате должно быть `CN=144.31.248.91`
- В клиенте `IPWS=144.31.248.91`
- Или используйте `disableSSLCertificateChecking()` (уже есть в коде)

**Ошибка: Close code 4000 (Device already connected)**

Это означает, что устройство с таким `deviceId` уже подключено.

**Решение:**

- Используйте уникальный `DEVICEID` для каждого клиента
- Или отключите предыдущее соединение

### 7.3 Проблемы с базой данных

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

### 7.4 Отладка SSL

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

---

## ЧАСТЬ 8: БЫСТРЫЙ СТАРТ (все команды)

### 8.1 На сервере (144.31.248.91)

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

### 8.2 На клиенте

```bash
#!/bin/bash

# 1. КЛОНИРОВАНИЕ
cd /opt
git clone https://github.com/ramanzes/dixu-v1-websocket-client.git
cd dixu-v1-websocket-client
git checkout dev4

# 2. НАСТРОЙКА
nano src/main/resources/config.properties
# Измените:
# DEVICEID=ваш-уникальный-id
# IPWS=144.31.248.91
# PORTWS=8443

# 3. СБОРКА
mvn clean package

# 4. ЗАПУСК
java -jar target/TestWebSocketClient-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## ЧАСТЬ 9: ПРОДАКШЕН РЕКОМЕНДАЦИИ

### 9.1 Безопасность

**1. Смените пароли:**

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

**2. Ограничьте доступ к файлам:**

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

### 9.2 Мониторинг

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

### 9.3 Резервное копирование

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

### 9.4 Использование реального SSL сертификата

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

## ЧАСТЬ 10: СПРАВОЧНАЯ ИНФОРМАЦИЯ

### 10.1 Структура проекта сервера

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

### 10.2 Структура проекта клиента

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

### 10.3 Важные параметры

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
DEVICEID=device-10                            # ID устройства (уникальный!)
IPWS=144.31.248.91                            # IP сервера
PORTWS=8443                                   # Порт сервера
AUTH_TOKEN=...                                # Токен аутентификации
DEBUG=true                                    # Режим отладки
LOCALWEBPORT=5000                             # Порт локального веб-сервера
```

### 10.4 Коды закрытия WebSocket

Сервер использует специальные коды закрытия:

- **4000** - Устройство уже подключено
- **4001** - Ошибка аутентификации (неверный токен)
- **4002** - Отсутствует deviceId
- **1000** - Нормальное закрытие
- **1008** - Нарушение политики

### 10.5 Полезные ссылки

- **Spring Boot WebSocket**: <https://spring.io/guides/gs/messaging-stomp-websocket/>
- **Java WebSocket API**: <https://docs.oracle.com/javaee/7/api/javax/websocket/package-summary.html>
- **Keytool документация**: <https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html>
- **Let's Encrypt**: <https://letsencrypt.org/>

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
