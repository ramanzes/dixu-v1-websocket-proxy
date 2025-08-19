# Используем официальный образ Amazon Corretto 21
FROM amazoncorretto:21

# Создаем рабочую директорию
WORKDIR /app

# Копируем сертификаты и конфигурационные файлы
COPY target/classes/truststore.jks /app/
COPY target/classes/keystore.p12 /app/
COPY target/classes/server.crt /app/
COPY src/main/resources/logback.xml /app/

# Копируем основной JAR файл
COPY target/websocket-proxy-0.0.1-SNAPSHOT.jar /app/app.jar

# Копируем базу данных
COPY device_tokens.db /app/
COPY mydb.sqlite /app/

# Открываем необходимые порты
EXPOSE 8443

# Устанавливаем переменные окружения
ENV BUFFER_SIZE=4096
ENV DEBUG=true
ENV COMPRESSMINSIZE=1024



# Запуск приложения с параметрами SSL
CMD ["java", \
     "-Djavax.net.ssl.trustStore=/app/truststore.jks", \
     "-Djavax.net.ssl.trustStorePassword=${TRUSTSTORE_PASSWORD}", \
     "-Djavax.net.ssl.trustStoreType=JKS", \
     "-Djavax.net.ssl.keyStore=/app/keystore.p12", \
     "-Djavax.net.ssl.keyStorePassword=${KEYSTORE_PASSWORD}", \
     "-Djavax.net.ssl.keyStoreType=PKCS12", \
     "-Dserver.ssl.keyAlias=tomcat", \
     "-Dserver.ssl.trust-store=/app/truststore.jks", \
     "-Dserver.ssl.trust-store-password=${TRUSTSTORE_PASSWORD}", \
     "-Dserver.ssl.trust-store-type=JKS", \
     "-Dserver.ssl.key-store=/app/keystore.p12", \
     "-Dserver.ssl.key-store-password=${KEYSTORE_PASSWORD}", \
     "-Dserver.ssl.key-store-type=PKCS12", \
     "-Dserver.ssl.key-alias=tomcat", \
     "-Dspring.datasource.url=jdbc:sqlite:file:./mydb.sqlite", \
     "-jar", "app.jar"]
