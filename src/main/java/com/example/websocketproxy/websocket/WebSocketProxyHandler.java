package com.example.websocketproxy.websocket;

import com.example.websocketproxy.service.DeviceSessionManager;
import com.example.websocketproxy.service.MyLogger;
import com.example.websocketproxy.service.RequestData;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.socket.BinaryMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
//public class WebSocketProxyHandler extends TextWebSocketHandler  {
public class WebSocketProxyHandler extends BinaryWebSocketHandler {

    private final DeviceSessionManager deviceSessionManager;
    private final Map<String, CompletableFuture<String>> responseFutures = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<byte[]>> binaryResponseFutures = new ConcurrentHashMap<>();

    //!!!!!!!!!!!!!буферы должны быть не для id устройства, а для id каждого запроса этого устройства

    private final Map<String, StringBuilder> textMessageBuffers = new ConcurrentHashMap<>(); // Буфер для фрагментированных сообщений
    private final Map<String, ByteArrayOutputStream> byteMessageBuffers = new ConcurrentHashMap<>();

  //  private final Map<String, String> lastContentTypes = new ConcurrentHashMap<>();

    public WebSocketProxyHandler(DeviceSessionManager deviceSessionManager) {
        this.deviceSessionManager = deviceSessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String deviceId = getDeviceIdFromSession(session);
        if (deviceId == null) {
            session.close(CloseStatus.BAD_DATA);
            MyLogger.logServer("Connection rejected: missing or invalid deviceId");
            return;
        }

        deviceSessionManager.addSession(deviceId, session);
        textMessageBuffers.put(deviceId, new StringBuilder()); // Инициализация буфера для устройства
        byteMessageBuffers.put(deviceId, new ByteArrayOutputStream()); // Инициализация буфера для устройства
        MyLogger.logServer("Device connected: " + deviceId);
    }


//Извлечение requestId из ответов
    private String extractRequestId(String message) {

        int headerEndIndex = message.indexOf("\r\n\r\n");
        if (headerEndIndex == -1) {
            MyLogger.logServer("No headers found in the response");
            return "";
        }

        String headers = message.substring(0, headerEndIndex);

        for (String line : headers.split("\r\n")) {
            if (line.startsWith("X-Request-Id:")) {
                return line.substring("X-Request-Id:".length()).trim();
            }
        }
        return null;
    }



    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {

        String payload = message.getPayload();

        MyLogger.logServer(payload.substring(0,25)+"...",true);
        try {
            // Парсим JSON
            JsonObject json = new Gson().fromJson(payload, JsonObject.class);

            String requestId = json.get("requestId").getAsString();

            String data = json.get("data").getAsString();

            String isLast = json.get("isLast").getAsString();


            // Обработка данных
            StringBuilder buffer = textMessageBuffers.computeIfAbsent(requestId, k -> new StringBuilder());
            buffer.append(data);

            if (isLast.equals("true")) {
                String fullMessage = buffer.toString();
                buffer.setLength(0); // Очищаем буфер
                textMessageBuffers.remove(requestId); // Удаляем буфер для requestId

                MyLogger.logServer(fullMessage);
//                // Если сообщение слишком большое, возможно, нужно добавить дополнительную обработку
//                if (fullMessage.length() > MAX_MESSAGE_SIZE) {
//                    MyLogger.logServer("Сообщение для requestId: " + requestId + " слишком большое, обрабатываем по частям.");
//                    // Можно добавить логику для обработки слишком больших сообщений
//                }
//
                if (fullMessage.startsWith("HTTP/1.1")) {
                    handleResponse(requestId, fullMessage);
                } else {
                    MyLogger.logServer("Full message for requestId: " + requestId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return true; // Включаем поддержку фрагментированных сообщений
    }


    //приём всех частей сообщения бинарного ответа

    @Override
    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String deviceId = getDeviceIdFromSession(session);
        if (deviceId == null) {
            MyLogger.logServer("Received message from unidentified session");
            return;
        }
        MyLogger.logServer("Начинаем приём бинарных сообщений от устройства [" + deviceId + "]");

        ByteBuffer payload = message.getPayload();
        payload.rewind();

        try {// Извлекаем длину requestId
//            int requestIdLength = deviceId.length()+1+36+Integer.BYTES; //1+uuid
            // Читаем длину requestId
            int requestIdLength = payload.getInt(); // Извлекаем 4 байта длины requestId

            byte[] requestIdBytes = new byte[requestIdLength];
            payload.get(requestIdBytes);
            String requestId = new String(requestIdBytes, StandardCharsets.UTF_8);

            MyLogger.logServer(requestId);
            // Остальные данные
            byte[] data = new byte[payload.remaining()];
            payload.get(data);




            // Сохраняем фрагменты в буфер
            ByteArrayOutputStream buffer = byteMessageBuffers.computeIfAbsent(requestId, k -> new ByteArrayOutputStream());
            buffer.write(data);

            // Если это последний фрагмент, обрабатываем сообщение
            if (message.isLast()) {
                byte[] fullMessageBytes = buffer.toByteArray();
//                buffer.reset(); // Очищаем буфер
                byteMessageBuffers.remove(requestId); // Удаляем буфер для requestId

                // Определяем contentType (если доступен)
                String contentType = RequestData.getContentTypeFromRequestId(requestId);
                if (contentType == null) {
                    handleBinaryResponse(requestId, fullMessageBytes, "application/octet-stream");
                } else {
//                    MyLogger.logServer("Получены данные для запроса [" + requestId + "]: " + Base64.getEncoder().encodeToString(fullMessageBytes));
                    // Вычисление хэша отправляемых данных
                    String dataHash = calculateHash(fullMessageBytes);
                    MyLogger.logServer("Хэш данных для запроса [" + requestId + "]: " + dataHash);

                    handleBinaryResponse(requestId, fullMessageBytes, contentType);
                    RequestData.removeContentTypeRequestId(requestId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private static String calculateHash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "Ошибка вычисления хэша";
        }
    }


    // найти все ключи содержащие diviceID
    private List<String> completeTextResponseFutures(String deviceId) {
        // Создаем список ключей, которые нужно удалить
        List<String> keysToRemove = new ArrayList<>();
        // Проходим по всем записям в responseFutures
        for (String key : responseFutures.keySet()) {
            if (key.contains(deviceId)) {
                // Если ключ содержит deviceId, добавляем его в список для удаления
                keysToRemove.add(key);
            }
        }
        return keysToRemove;
    }

    // найти все ключи содержащие diviceID
    private List<String> completeBinaryResponseFutures(String deviceId) {
        // Создаем список ключей, которые нужно удалить
        List<String> keysToRemove = new ArrayList<>();
        // Проходим по всем записям в responseFutures
        for (String key : binaryResponseFutures.keySet()) {
            if (key.contains(deviceId)) {
                // Если ключ содержит deviceId, добавляем его в список для удаления
                keysToRemove.add(key);
            }
        }
        return keysToRemove;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {

        String deviceId = getDeviceIdFromSession(session);

        // Завершаем все CompletableFuture для этого устройства
        CompletableFuture<String> textFuture;// = responseFutures.remove();
        CompletableFuture<byte[]> binaryFuture;// = binaryResponseFutures.remove(deviceId);


        //здесь добавить для текстового запроса или для бинарного как в ProxyController, а не оба сразу

            if (deviceId != null) {
                // Завершаем все CompletableFuture для найденных ключей
                for (String key : completeTextResponseFutures(deviceId)) {
                    textFuture = responseFutures.remove(key);
                    if (textFuture != null && !textFuture.isDone()) {
                        textFuture.completeExceptionally(new IOException("Connection closed"));
                        MyLogger.logServer("Completed text future for device " + deviceId + " due to connection close");
                        break;
                    }
                }

                for (String key : completeBinaryResponseFutures(deviceId)) {
                    binaryFuture = binaryResponseFutures.remove(key);
                    if (binaryFuture != null && !binaryFuture.isDone()) {
                        binaryFuture.completeExceptionally(new IOException("Connection closed"));
                        MyLogger.logServer("Completed binary future for device " + deviceId + " due to connection close");
                        break;
                    }
                }
                }
            // Удаляем сессию устройства
            deviceSessionManager.removeSession(deviceId);
            MyLogger.logServer("Device disconnected: " + deviceId);
        }


    public CompletableFuture<String> waitForResponse(String requestId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        MyLogger.logServer("ждём текстовые данные для устройства requestId: " + requestId);
        responseFutures.put(requestId, future);
        return future;
    }

    public CompletableFuture<byte[]> waitForBinaryResponse(String requestId) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        MyLogger.logServer("ждём бинарные данные для устройства requestId: " + requestId);
        binaryResponseFutures.put(requestId, future);
        return future;
    }




    // при получении полного текстового ответа выполнение  future.complete(response);
    private void handleResponse(String requestId, String response) throws MyLogger.CustomException {
        if (requestId == null) {
            MyLogger.logServer("No requestId found in the response");
            throw new MyLogger.CustomException("получили сообщение для неизвестного request id","request id null");
        }

        // Ищем CompletableFuture по requestId
        CompletableFuture<String> future = responseFutures.remove(requestId);
        if (future != null) {
            MyLogger.logServer("Handling response for device requestId: " + requestId);
            future.complete(response);
        } else {
            MyLogger.logServer("Unexpected response from device, requestId: " + requestId);
        }
    }




    // при получении полного бинарного ответа выполнение  future.complete(response);
    private void handleBinaryResponse(String requestId, byte[] data, String contentType) {

        if (requestId == null) {
            MyLogger.logServer("No requestId found in the binary response");
            return;
        }

        // Ищем CompletableFuture по requestId
        CompletableFuture<byte[]> future = binaryResponseFutures.remove(requestId);
        if (future != null) {
            MyLogger.logServer("Handling binary response for device, requestId: " + requestId);

            //правильные заголовки в этой data есть? сейчас
            future.complete(data);
        } else {
            MyLogger.logServer("Unexpected binary response from device, requestId: " + requestId);
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



