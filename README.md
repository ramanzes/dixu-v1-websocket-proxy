
### на сервере должен быть открыт порт на котором работает сервер. по умолчанию 8080
#### указан в настройках websocket-proxy/src/main/resources/application.properties

```bash
sudo iptables -A INPUT -p tcp --dport 8080 -j ACCEPT
```

