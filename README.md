
### на сервере должен быть открыт порт на котором работает сервер. по умолчанию 8080
#### указан в настройках websocket-proxy/src/main/resources/application.properties

```bash
sudo iptables -A INPUT -p tcp --dport 8080 -j ACCEPT
iptables-save > /etc/iptables/rules.v4
iptables-restore < /etc/iptables/rules.v4
```

если Ошибка iptables: Failed to initialize nft: Protocol not supported указывает на то, что ваша система не поддерживает nftables, который является новым интерфейсом для управления сетевыми фильтрами в Linux. Это может произойти, если ваш ядро не поддерживает nftables или если вы используете устаревшую версию iptables

```bash
apk add iptables-legacy

sudo iptables-legacy -A INPUT -p tcp --dport 8080 -j ACCEPT
iptables-legacy-save > /etc/iptables/rules.v4
iptables-legacy-restore < /etc/iptables/rules.v4
```
