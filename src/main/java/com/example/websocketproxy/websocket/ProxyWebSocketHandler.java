package com.example.websocketproxy.websocket;

import com.example.websocketproxy.services.DeviceSessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class ProxyWebSocketHandler extends TextWebSocketHandler {
    // Объявление логгера

    private final DeviceSessionManager deviceSessionManager;

    //!!! пока не понял зачем мне это
    //    private final ConcurrentHashMap<String, LinkedBlockingQueue<String>> responseQueues = new ConcurrentHashMap<>();

    public ProxyWebSocketHandler(DeviceSessionManager deviceSessionManager) {
        this.deviceSessionManager = deviceSessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String deviceId = deviceSessionManager.getDeviceIdFromSession(session);// getDeviceId(session);
        if (deviceId != null) {
            deviceSessionManager.addSession(deviceId, session);
//            responseQueues.put(deviceId, new LinkedBlockingQueue<>());
            System.out.println("Device connected: " + deviceId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String deviceId = deviceSessionManager.getDeviceIdFromSession(session); // getDeviceId(session);
        if (deviceId != null) {
            // Сохраняем ответ от устройства в соответствующую очередь
//            responseQueues.get(deviceId).offer(message.getPayload());
        }
    }

//    @OnMessage
//    public void onMessage(String message, WebSocketSession session) {
//       MyLogger.processMessage(message);
//    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String deviceId = deviceSessionManager.getDeviceIdFromSession(session); // getDeviceId(session);
        if (deviceId != null) {
            deviceSessionManager.removeSession(deviceId);
//            responseQueues.remove(deviceId);
            System.out.println("Device disconnected: " + deviceId);
        }
    }


//
//    private String getDeviceId(WebSocketSession session) {
//        try {
//            String query = session.getUri().getQuery();
//            if (query != null && query.contains("deviceId=")) {
//                return query.split("deviceId=")[1];
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
}



