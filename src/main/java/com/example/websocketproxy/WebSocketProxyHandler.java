package com.example.websocketproxy;

import com.example.websocketproxy.service.DeviceSessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.BinaryMessage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
//public class WebSocketProxyHandler extends TextWebSocketHandler  {
public class WebSocketProxyHandler extends BinaryWebSocketHandler {

    private final DeviceSessionManager deviceSessionManager;
    private final Map<String, CompletableFuture<String>> responseFutures = new ConcurrentHashMap<>();
    private final Map<String, StringBuilder> messageBuffers = new ConcurrentHashMap<>(); // Буфер для фрагментированных сообщений

    public WebSocketProxyHandler(DeviceSessionManager deviceSessionManager) {
        this.deviceSessionManager = deviceSessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String deviceId = getDeviceIdFromSession(session);
        if (deviceId == null) {
            session.close(CloseStatus.BAD_DATA);
            System.out.println("Connection rejected: missing or invalid deviceId");
            return;
        }

        deviceSessionManager.addSession(deviceId, session);
        messageBuffers.put(deviceId, new StringBuilder()); // Инициализация буфера для устройства
        System.out.println("WebSocketProxyHandler.afterConnectionEstablished Device connected: " + deviceId);
    }


    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        String deviceId = getDeviceIdFromSession(session);
        if (deviceId == null) {
            System.out.println("Received message from unidentified session");
            return;
        } else System.out.println("начинаем приём сообщений от устройства"+deviceId);

        boolean isLast = message.isLast(); // Проверяем, является ли это последней частью
        String payload = message.getPayload();

        // Получаем буфер для устройства
        StringBuilder buffer = messageBuffers.get(deviceId);
        if (buffer == null) {
            System.out.println("Buffer not initialized for device " + deviceId);
            return;
        }

        // Добавляем часть сообщения в буфер
        buffer.append(payload);
        System.out.print("handleTextMessage:");
        System.out.println("получена часть сообщения: " + payload);

        // Если это последняя часть, обрабатываем сообщение
        if (isLast && buffer != null) {
            String fullMessage = buffer.toString();
            buffer.setLength(0); // Очищаем буфер

            // Если это ответ на запрос, передаем его в future
            if (fullMessage.startsWith("HTTP/1.1")) {
                handleResponse(deviceId, fullMessage);
            } else {
                // Обработка других сообщений (если нужно)
                System.out.println("Full message from device " + deviceId + ": " + fullMessage);
            }
        }
    }
    @Override
    public boolean supportsPartialMessages() {
        return true; // Включаем поддержку фрагментированных сообщений
    }
    @Override
    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String deviceId = getDeviceIdFromSession(session);
        if (deviceId == null) {
            System.out.println("Received binary message from unidentified session");
            return;
        }

        boolean isLast = message.isLast(); // Проверяем, является ли это последней частью
        ByteBuffer payload = message.getPayload();

        // Получаем буфер для устройства
        StringBuilder buffer = messageBuffers.get(deviceId);
        if (buffer == null) {
            System.out.println("Buffer not initialized for device " + deviceId);
            return;
        }

        // Добавляем часть сообщения в буфер
        StringBuilder append = buffer.append(new String(((ByteBuffer) payload).array(), StandardCharsets.UTF_8));

        System.out.print("handleBinaryMessage: ");
        System.out.println("получена часть сообщения: " + payload.remaining() + " байт");

        // Если это последняя часть, обрабатываем сообщение
        if (isLast) {
            String fullMessage = append.toString();
            append.setLength(0); // Очищаем буфер

            // Если это ответ на запрос, передаем его в future
            if (fullMessage.startsWith("HTTP/1.1")) {
                handleResponse(deviceId, fullMessage);
            } else {
                // Обработка других сообщений (если нужно)
                System.out.println("Full message from device " + deviceId + ": " + fullMessage);
            }
        }
    }



    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String deviceId = getDeviceIdFromSession(session);
        if (deviceId != null) {
            deviceSessionManager.removeSession(deviceId);
            responseFutures.remove(deviceId);
            System.out.println("Device disconnected: " + deviceId);
        }
    }

    public CompletableFuture<String> waitForResponse(String deviceId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        responseFutures.put(deviceId, future);

        return future;
    }

    private void handleResponse(String deviceId, String response) {
        //связаны по deviceId в Map. получаем
        CompletableFuture<String> future = responseFutures.remove(deviceId);
        if (future != null) {
            System.out.println("Handling response for device " + deviceId + ": " + response);
            //создаём событие завершения
            future.complete(response);
        } else {
            System.out.println("Unexpected response from device " + deviceId + ": " + response);
        }
    }


    private String getDeviceIdFromSession(WebSocketSession session) {
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
}






//package com.example.websocketproxy;
//
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.CloseStatus;
//import org.springframework.web.socket.TextMessage;
//import org.springframework.web.socket.WebSocketSession;
//import org.springframework.web.socket.handler.TextWebSocketHandler;
//
//import java.util.Map;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.LinkedBlockingQueue;
//
//@Component
//public class WebSocketProxyHandler extends TextWebSocketHandler {
//    private final ConcurrentHashMap<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();
//    private final ConcurrentHashMap<String, LinkedBlockingQueue<String>> responseQueues = new ConcurrentHashMap<>();
//    private final Map<String, CompletableFuture<String>> responseFutures = new ConcurrentHashMap<>();
//
//    public WebSocketProxyHandler(WebSocketProxyHandler session){
//
//    }
//
//    @Override
//    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//        String deviceId = session.getUri().getQuery().split("=")[1];
//        deviceSessions.put(deviceId, session);
//
//        System.out.println("ProxyController.getSession !"+deviceSessions);
//
//        responseQueues.put(deviceId, new LinkedBlockingQueue<>());
//        System.out.println("Device connected: " + deviceId);
//    }
//
//    @Override
//    public void handleTextMessage(WebSocketSession session, TextMessage message) {
//        String deviceId = getDeviceIdFromSession(session); // Метод для получения ID устройства
//        String response = message.getPayload();
//
//        // Если это ответ на запрос, передаем его в future
//        if (response.startsWith("HTTP/1.1")) {
//            handleResponse(deviceId, response);
//        } else {
//            // Обработка других сообщений (если нужно)
//            System.out.println("Message from device " + deviceId + ": " + response);
//        }
//    }
//
//
//    @Override
//    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
//        String deviceId = getDeviceIdFromSession(session);
//        if (deviceId != null) {
//            deviceSessions.remove(deviceId);
//            responseQueues.remove(deviceId);
//            System.out.println("Device disconnected: " + deviceId);
//        }
//    }
//
//    public WebSocketSession getSession(String deviceId) {
//        //      System.out.println("ProxyController.getSession "+ds.toString());
//        return deviceSessions.get(deviceId);
//    }
//
//    public String getResponseForRequest(String deviceId) throws InterruptedException {
//        // Ждем ответа от устройства
//        return responseQueues.get(deviceId).take();
//    }
//    public CompletableFuture<String> waitForResponse(String deviceId) {
//        CompletableFuture<String> future = new CompletableFuture<>();
//        responseFutures.put(deviceId, future);
//        System.out.println("WebSocketProxyHandler.waitForResponse:"+future);
//        return future;
//    }
//
//    public void handleResponse(String deviceId, String response) {
//        CompletableFuture<String> future = responseFutures.remove(deviceId);
//        if (future != null) {
//            future.complete(response);
//        }
//    }
//
//
//
//    private String getDeviceIdFromSession(WebSocketSession session) {
//        try {
//            String query = session.getUri().getQuery();
//            if (query != null && query.contains("deviceId=")) {
//                return query.split("=")[1];
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
//}
