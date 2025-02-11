package com.example.websocketproxy.services.logsandexceptions.exceptions;

public class DeviceWithThisIdIsInActiveSessionNow extends RuntimeException{
    public DeviceWithThisIdIsInActiveSessionNow(){
        super("Устройство с таким же ID уже зарегистрировано на прокси сервере. Клиенту сообщено обратиться в райсполком, где ему выдавали ID для его устройства");
    }
}
