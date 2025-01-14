package com.example.websocketproxy;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class WebSocketProxyHandler extends TextWebSocketHandler {
    private final ConcurrentHashMap<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LinkedBlockingQueue<String>> responseQueues = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String deviceId = session.getUri().getQuery().split("=")[1];
        deviceSessions.put(deviceId, session);
        responseQueues.put(deviceId, new LinkedBlockingQueue<>());
        System.out.println("Device connected: " + deviceId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String deviceId = getDeviceIdFromSession(session);
        if (deviceId != null) {
            // Добавляем ответ в очередь для данного устройства
            responseQueues.get(deviceId).offer(message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String deviceId = getDeviceIdFromSession(session);
        if (deviceId != null) {
            deviceSessions.remove(deviceId);
            responseQueues.remove(deviceId);
            System.out.println("Device disconnected: " + deviceId);
        }
    }

    public WebSocketSession getSession(String deviceId) {
        return deviceSessions.get(deviceId);
    }

    public String getResponseForRequest(String deviceId) throws InterruptedException {
        // Ждем ответа от устройства
        return responseQueues.get(deviceId).take();
    }

    private String getDeviceIdFromSession(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery();
            if (query != null && query.contains("deviceId=")) {
                return query.split("=")[1];
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
