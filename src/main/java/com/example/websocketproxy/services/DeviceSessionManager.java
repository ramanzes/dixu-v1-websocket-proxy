package com.example.websocketproxy.services;

import com.example.websocketproxy.services.logsandexceptions.exceptions.DeviceWithThisIdIsInActiveSessionNow;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class DeviceSessionManager {
    //ключом является idDevice
//    private final Map<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();

    private final Map<String, ThisDevice> deviceSessions = new ConcurrentHashMap<>();

    //поддерживает ли локальный сервер сжатие. данные обновляются при get запросах к устройству на контроллере
    private final Map<String, Boolean> deviceLocalhostZipMethod = new ConcurrentHashMap<>();

    //    private final Map<String, Map<String, String>> deviceCookies = new ConcurrentHashMap<>(); // Сохранение кук
//
    public void addSession(String deviceId, WebSocketSession session) {
        if (isDeviceConnected(deviceId)) throw new DeviceWithThisIdIsInActiveSessionNow();
        deviceSessions.put(deviceId, new ThisDevice(deviceId,session));
    }


    //метод добавляее информацию о локальном севрвере, о поддержке сжатия, на основе заголовков
    public void addLocalhostInfoZip(String deviceId, Boolean result) {
        if (!isDeviceConnected(deviceId)) throw new DeviceWithThisIdIsInActiveSessionNow();
        deviceLocalhostZipMethod.put(deviceId, result);
    }

//    // Добавление кук
//    public void addCookies(String requestId, Map<String, String> cookies) {
//        deviceCookies.put(requestId, cookies);
//    }
//
//    // Получение кук
//    public Map<String, String> getCookies(String requestId) {
//        return deviceCookies.getOrDefault(requestId, new ConcurrentHashMap<>());
//    }

    public void removeSession(String deviceId) {
        deviceSessions.remove(deviceId);
    }

    public WebSocketSession getSession(String deviceId) {
        return deviceSessions.get(deviceId).session;
    }

    public boolean isDeviceConnected(String deviceId) {
        return deviceSessions.containsKey(deviceId);
    }

    public Map<String, ThisDevice> getAllSessions() {
        return deviceSessions;
    }


    //получаем id устройства из параметров сессии
    public String getDeviceIdFromSession(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery();
            if (query != null && query.contains("deviceId=")) {
                return query.split("deviceId=")[1];
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }






    //private final Map<String,WebSocketSession> sessionToRequestId = new ConcurrentHashMap<>();


//    public void addRequestIdSession(WebSocketSession session, String requestId) {
//        sessionToRequestId.put(session, requestId);
//    }
//
//    public String getRequestIdSession(WebSocketSession session) {
//        return sessionToRequestId.get(session);
//    }
//
//    public boolean isFirstForRequestId(WebSocketSession session){
//        return !sessionToRequestId.containsKey(session);
//    }
//
//    public void removeRequestIdSession(String requestId) {
//        sessionToRequestId.remove(requestId);
//    }


}

class ThisDevice{
    String id="";
    WebSocketSession session = null;
    Boolean localservWithCompress = false;
    String methodCompress = "";

    public ThisDevice(String id,WebSocketSession session) {
        this.id = id;
        this.session = session;
    }

    public ThisDevice(String id, WebSocketSession session, Boolean localservWithCompress, String methodCompress) {
        this.id = id;
        this.session = session;
        this.localservWithCompress = localservWithCompress;
        this.methodCompress = methodCompress;
    }
}
