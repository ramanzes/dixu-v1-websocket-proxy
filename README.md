
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




здесь на прокси нужно перейти на реактивный спринг, а также поднять свой локальный сервер 
который будет отвечать за поддержку сжатых ответов и формировать заголовки ответа для пользователей
чьи методы сжатия например не поддерживаются на локальном сервере устройства, или если на локальном 
вообще нет поддержки сжатия



!!! всю информацию об устройстве нужно делать на самом клиенте, и отдельным каналом передавать на прокси,
чтобы не проделывать повторную работу выяснения методов сжатия, исходя из заголовков ответа устройства
