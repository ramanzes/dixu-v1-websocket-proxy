package com.websocketproxy.services;

import com.websocketproxy.repository.Devices;
import com.websocketproxy.services.logsandexceptions.exceptions.DeviceWithThisIdIsInActiveSessionNow;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//класс для устройств и сессий по вебсокету
@Component
public class DeviceSessionManager {
    //ключом является idDevice
//    private final Map<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();

    private static DeviceSessionManager instance;

    //конструктор вызывается один раз
    public DeviceSessionManager() {}

    // Статический метод для получения экземпляра класса
    public static DeviceSessionManager getInstance() {
        if (instance == null) {
            instance = new DeviceSessionManager();
        }
        return instance;
    }


    private final Map<String, Devices> deviceSessions = new ConcurrentHashMap<>();

    public void addDeviceWithSession(String deviceId, WebSocketSession session) {
        if (isThisDeviceConnected(deviceId)) throw new DeviceWithThisIdIsInActiveSessionNow();
        deviceSessions.put(deviceId, new Devices(deviceId,session));
    }

    public void removeDeviceWithSession(String deviceId) {
        deviceSessions.remove(deviceId);
    }
    public Devices getThisDevice(String deviceId){
     return deviceSessions.get(deviceId);
    }

    public WebSocketSession getSessionForThisDevice(String deviceId) {
        return deviceSessions.get(deviceId).getSession();
    }

    public boolean isThisDeviceConnected(String deviceId) {
        return deviceSessions.containsKey(deviceId);
    }

    public Map<String, Devices> getAllDevices() {
        return deviceSessions;
    }


    final public String getDeviceIdFromThisSession(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] keyValue = param.split("=", 2);
                    if (keyValue.length == 2 && keyValue[0].equals("deviceId")) {
                        return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }




    public String getTokenFromThisSession(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] keyValue = param.split("=", 2);
                    if (keyValue.length == 2 && keyValue[0].equals("token")) {
                        return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    }
                }
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

