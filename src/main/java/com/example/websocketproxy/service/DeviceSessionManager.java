package com.example.websocketproxy.service;

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




}
