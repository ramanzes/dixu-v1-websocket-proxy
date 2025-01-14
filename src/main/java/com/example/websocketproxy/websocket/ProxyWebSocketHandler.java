package com.example.websocketproxy.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class ProxyWebSocketHandler extends TextWebSocketHandler {
    private final Map<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String deviceId = getDeviceId(session);
        if (deviceId != null) {
            deviceSessions.put(deviceId, session);
            System.out.println("Device connected: " + deviceId);
        }
        System.out.println("New WebSocket connection: " + session.getUri());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String deviceId = getDeviceId(session);
        System.out.println("Message from device " + deviceId + ": " + message.getPayload());

        // Пример обработки сообщений от конечного пользователя
        String userRequest = "HTTP request from user";
        WebSocketSession deviceSession = deviceSessions.get(deviceId);
        if (deviceSession != null && deviceSession.isOpen()) {
            deviceSession.sendMessage(new TextMessage(userRequest));
        }
    }

    private String getDeviceId(WebSocketSession session) {
        return session.getUri().getQuery().split("=")[1];
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String deviceId = getDeviceId(session);
        if (deviceId != null) {
            deviceSessions.remove(deviceId);
            System.out.println("Device disconnected: " + deviceId);
        }
    }
        // Метод для получения сессии устройства по его идентификатору
    public WebSocketSession getDeviceSessions(String deviceId) {
        return deviceSessions.get(deviceId);
    }

}



//@Component
//public class ProxyWebSocketHandler extends TextWebSocketHandler {
//
//    // Хранилище WebSocket-сессий для устройств
//    private final ConcurrentHashMap<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();
//
//    @Override
//    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//        // Уникальный идентификатор устройства из параметра URL (например, /ws?deviceId=123)
//        String deviceId = session.getUri().getQuery().split("=")[1];
//        deviceSessions.put(deviceId, session);
//        System.out.println("Device connected: " + deviceId);
//    }
//
//    @Override
//    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
//        // Лог входящих сообщений от устройств (или обработки по необходимости)
//        System.out.println("Message from device: " + message.getPayload());
//    }
//
//    @Override
//    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
//        // Удаляем устройство из списка активных сессий
//        String deviceId = session.getUri().getQuery().split("=")[1];
//        deviceSessions.remove(deviceId);
//        System.out.println("Device disconnected: " + deviceId);
//    }
//
//    // Метод для получения сессии устройства по его идентификатору
//    public WebSocketSession getDeviceSession(String deviceId) {
//        return deviceSessions.get(deviceId);
//    }
//}
