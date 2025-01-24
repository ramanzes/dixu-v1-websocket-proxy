package com.example.websocketproxy.service;

import com.example.websocketproxy.config.WebSocketConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeviceSessionManager {
    private final Map<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();

    public void addSession(String deviceId, WebSocketSession session) {
        deviceSessions.put(deviceId, session);
    }

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
