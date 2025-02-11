package com.example.websocketproxy.services;

import com.example.websocketproxy.services.logsandexceptions.exceptions.DeviceWithThisIdIsInActiveSessionNow;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeviceSessionManager {
    private final Map<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();
//    private final Map<String, Map<String, String>> deviceCookies = new ConcurrentHashMap<>(); // Сохранение кук
//
    public void addSession(String deviceId, WebSocketSession session) {
        if (isDeviceConnected(deviceId)) throw new DeviceWithThisIdIsInActiveSessionNow();
        deviceSessions.put(deviceId, session);
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
        return deviceSessions.get(deviceId);
    }

    public boolean isDeviceConnected(String deviceId) {
        return deviceSessions.containsKey(deviceId);
    }

    public Map<String, WebSocketSession> getAllSessions() {
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
