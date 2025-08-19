#!/usr/bin/env bash
echo "баш или аш зависит от вашей оболочки"
#java -cp target/websocket-proxy-0.0.1-SNAPSHOT-jar-with-dependencies.jar WebSocketProxyApplication
#спринг запускается вот так
#java -jar target/websocket-proxy-0.0.1-SNAPSHOT.jar







# Сборка образа с переменными окружения
docker build --build-arg TRUSTSTORE_PASSWORD=$(cat .env | grep TRUSTSTORE_PASSWORD | cut -d '=' -f2) \
             --build-arg KEYSTORE_PASSWORD=$(cat .env | grep KEYSTORE_PASSWORD | cut -d '=' -f2) \
             -t websocket-proxy .

# Запуск контейнера с переменными окружения
docker run -it -p 8443:8443 -e TRUSTSTORE_PASSWORD=$(cat .env | grep TRUSTSTORE_PASSWORD | cut -d '=' -f2) \
                                      -e KEYSTORE_PASSWORD=$(cat .env | grep KEYSTORE_PASSWORD | cut -d '=' -f2) \
                                      websocket-proxy
