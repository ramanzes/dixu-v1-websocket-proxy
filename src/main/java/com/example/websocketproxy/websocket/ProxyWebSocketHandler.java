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





//package com.example.websocketproxy.websocket;
//
//import com.example.websocketproxy.service.DeviceSessionManager;
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.CloseStatus;
//import org.springframework.web.socket.TextMessage;
//import org.springframework.web.socket.WebSocketSession;
//import org.springframework.web.socket.handler.TextWebSocketHandler;
//
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//
//
//@Component
//public class ProxyWebSocketHandler extends TextWebSocketHandler {
////    private final Map<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();
//    private final DeviceSessionManager deviceSessionManager;
//
//    public ProxyWebSocketHandler(DeviceSessionManager deviceSessionManager) {
//        this.deviceSessionManager = deviceSessionManager;
//    }
//
//    @Override
//    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//        String deviceId = getDeviceId(session);
//        if (deviceId != null) {
//            //deviceSessions.put(deviceId, session);
//            deviceSessionManager.addSession(deviceId, session);
//
//            System.out.println("ProxyWebSocketHandler.afterConnectionEstablished "+getDeviceSessions(deviceId));
//            System.out.println("Device connected: " + deviceId);
//        }
//        System.out.println("New WebSocket connection: " + session.getUri());
//    }
//
////    @Override
////    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
////        String deviceId = getDeviceId(session);
////        System.out.println("Message from device " + deviceId + ": " + message.getPayload());
////
////        // Пример обработки сообщений от конечного пользователя
////        String userRequest = "HTTP request from user";
////        WebSocketSession deviceSession = getDeviceSessions(deviceId);
////        if (deviceSession != null && deviceSession.isOpen()) {
////            deviceSession.sendMessage(new TextMessage(userRequest));
////        }
////    }
//
//
//    @Override
//    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
//        String deviceId = getDeviceId(session);
//        if (deviceId != null) {
//            // Логирование сообщения
//            System.out.println("Message from device " + deviceId + ": " + message.getPayload());
//
//            // Отправляем ответ клиенту
//            WebSocketSession clientSession = deviceSessionManager.getSession(deviceId); // Проверка сессии клиента
//            if (clientSession != null && clientSession.isOpen()) {
//                clientSession.sendMessage(message); // Отправка клиенту оригинального сообщения
//            }
//        }
//    }
//
//    private String getDeviceId(WebSocketSession session) {
//        return session.getUri().getQuery().split("=")[1];
//    }
//
//
//    @Override
//    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
//        String deviceId = getDeviceId(session);
//        if (deviceId != null) {
//           // deviceSessions.remove(deviceId);
//            deviceSessionManager.removeSession(deviceId);
//            System.out.println("Device disconnected: " + deviceId);
//        }
//    }
//        // Метод для получения сессии устройства по его идентификатору
//    public WebSocketSession getDeviceSessions(String deviceId) {
//        return deviceSessionManager.getSession(deviceId);
//    }
//
//
//}
//


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
