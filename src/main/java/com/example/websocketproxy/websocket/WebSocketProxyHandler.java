package com.example.websocketproxy.websocket;

import com.example.websocketproxy.services.DeviceSessionManager;
import com.example.websocketproxy.services.logsandexceptions.MyLogger;
import com.example.websocketproxy.services.logsandexceptions.exceptions.DeviceWithThisIdIsInActiveSessionNow;
import com.example.websocketproxy.services.logsandexceptions.exceptions.MyOtherExceptions;
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

    private final Map<String, ByteArrayOutputStream> headersThisResponse = new ConcurrentHashMap<>();

  //  private final Map<String, String> lastContentTypes = new ConcurrentHashMap<>();

    public WebSocketProxyHandler(DeviceSessionManager deviceSessionManager) {
        this.deviceSessionManager = deviceSessionManager;
    }


    // здесь мы общаемся с устройствами через вебсокет напрямую, ещё до первых запросов с контроллера
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        MyLogger.logServer("session.id = ["+session.getId()+"]");
        String deviceId = deviceSessionManager.getDeviceIdFromSession(session);
        if (deviceId == null) {
            session.close(CloseStatus.BAD_DATA);
            MyLogger.logServer("Connection rejected: missing or invalid deviceId");
            return;
        }

        // Проверка существующего подключения
        if (deviceSessionManager.isDeviceConnected(deviceId)) {
            // Закрываем соединение с специальным статусом
            CloseStatus closeStatus = new CloseStatus(
                    4000,
                    "Device " + deviceId + " already connected"
            );

            // Отправка специального сообщения перед закрытием
            try {
                session.sendMessage(new TextMessage(
                        "CONNECTION_REJECTED: Device already connected "+" Устройство с таким же ID уже зарегистрировано на прокси сервере. Обратитесь в райсполком, где вам выдавали ID для вашего устройства"
                ));
            } catch (IOException e) {
                MyLogger.logServer("Error sending rejection message: " + e.getMessage());
            }

            session.close(closeStatus);
            throw new DeviceWithThisIdIsInActiveSessionNow();
        }




        deviceSessionManager.addSession(deviceId, session);
        textMessageBuffers.put(deviceId, new StringBuilder()); // Инициализация буфера для устройства
        byteMessageBuffers.put(deviceId, new ByteArrayOutputStream()); // Инициализация буфера для устройства
        MyLogger.logServer("Device connected: " + deviceId);
    }



// когда сообщения по вебсокету приходят от устройства мы попадаем сюда
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {

        String payload = message.getPayload();

        MyLogger.logServer(payload.substring(0,49)+"...",true);
        try {
            // Парсим JSON
            JsonObject json = new Gson().fromJson(payload, JsonObject.class);

            String requestId = json.get("requestId").getAsString();
            //пока не передаём в клиенте в  json этот параметр
//            String cookies = json.get("cookies").getAsString();

            String data = json.get("data").getAsString();

            String isLast = json.get("isLast").getAsString();


            // Обработка данных получаем все части в буфер(в мапу), с ключом для каждого id запроса
            StringBuilder buffer = textMessageBuffers.computeIfAbsent(requestId, k -> new StringBuilder());
            buffer.append(data);

            if (isLast.equals("true")) {
                String fullMessage = buffer.toString();
                buffer.setLength(0); // Очищаем буфер
                textMessageBuffers.remove(requestId); // Удаляем буфер для requestId

//                MyLogger.logServer(fullMessage,true);
                MyLogger.logServer("Full response for requestId " + requestId + ": " + fullMessage);
//                // Если сообщение слишком большое, возможно, нужно добавить дополнительную обработку
//                if (fullMessage.length() > MAX_MESSAGE_SIZE) {
//                    MyLogger.logServer("Сообщение для requestId: " + requestId + " слишком большое, обрабатываем по частям.");
//                    // Можно добавить логику для обработки слишком больших сообщений
//                }
//
                if (fullMessage.startsWith("HTTP/1.1")) {
//                    handleResponse(requestId, fullMessage, cookies);
                    // Извлекаем заголовки для логирования
                    String headers = "";
                    int index = fullMessage.indexOf("\r\n\r\n");
                    if (index != -1) {
                        headers = fullMessage.substring(0, index);

                    String[] header = headers.split("\r\n");
                    for (String h : header) {
                        if (h.contains(":")) {
                            String[] keyValue = h.split(":", 2);
                            MyLogger.logServer("!!!заголовки на отправку на прокси["+keyValue[0].trim()+"]"+"["+keyValue[1].trim()+"]",true);
                        }
                    }
                    } else {
                        MyLogger.logServer("заголовки на отправку на прокси!!! неверный формат",true);
                    }

                    MyLogger.logServer("длина полного сообщения ["+requestId+"] "+fullMessage.length());
                    MyLogger.logServer("длина заголовков этого сообщения ["+requestId+"] "+headers.length());

                    handleResponse(requestId, fullMessage);
                } else {
                    MyLogger.logServer("[нет вначале HTTP/1.1] Full message for requestId: " + requestId,true);
                    throw new MyOtherExceptions("нет заголовка HTTP/1.1 у текстового типа данных");
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



    private final Map<String, Object> bufferLocks = new ConcurrentHashMap<>();

    private Object getBufferLock(String requestId) {
        return bufferLocks.computeIfAbsent(requestId, k -> new Object());
    }



    //приём всех частей сообщения бинарного ответа

//    @Override
//    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
//        String deviceId = deviceSessionManager.getDeviceIdFromSession(session);
//        if (deviceId == null) {
//            MyLogger.logServer("Received message from unidentified session",true);
//            return;
//        }
//        MyLogger.logServer("Начинаем приём бинарных сообщений от устройства [" + deviceId + "]",true);
//
//        ByteBuffer payload = message.getPayload();
//        payload.rewind();
//
//        try {// Извлекаем длину requestId
////            int requestIdLength = deviceId.length()+1+36+Integer.BYTES; //1+uuid
//            // Читаем длину requestId
//            int requestIdLength = payload.getInt(); // Извлекаем 4 байта длины requestId
//
//            byte[] requestIdBytes = new byte[requestIdLength];
//            payload.get(requestIdBytes);
//            String requestId = new String(requestIdBytes, StandardCharsets.UTF_8);
//
//            MyLogger.logServer(requestId,true);
//            // Остальные данные
//            byte[] data = new byte[payload.remaining()];
//            payload.get(data);
//
//
//
//
//            // Синхронизация на уровне requestId
//            synchronized (getBufferLock(requestId)) {
//                ByteArrayOutputStream buffer = byteMessageBuffers.computeIfAbsent(requestId, k -> new ByteArrayOutputStream());
//                buffer.write(data);
//
//                if (message.isLast()) {
//                    byte[] fullMessageBytes = buffer.toByteArray();
//                    byteMessageBuffers.remove(requestId);
//                    bufferLocks.remove(requestId); // Удаляем монитор
//                    handleBinaryResponse(requestId, fullMessageBytes);
//                }
//            }
//
//
////
////            // Сохраняем фрагменты в буфер для каждого requestId
////            ByteArrayOutputStream buffer = byteMessageBuffers.computeIfAbsent(requestId, k -> new ByteArrayOutputStream());
////            buffer.write(data);
////
////            // Если это последний фрагмент, обрабатываем сообщение
////            if (message.isLast()) {
////                byte[] fullMessageBytes = buffer.toByteArray();
//////                buffer.reset(); // Очищаем буфер
////                byteMessageBuffers.remove(requestId); // Удаляем буфер для requestId
////
////                handleBinaryResponse(requestId, fullMessageBytes);
////            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    private String extractRequestId(ByteBuffer payload) {
        int requestIdLength = payload.getInt(); // Извлекаем 4 байта длины requestId
        byte[] requestIdBytes = new byte[requestIdLength];
        payload.get(requestIdBytes);
        return new String(requestIdBytes, StandardCharsets.UTF_8);
    }

    private byte[] extractData(ByteBuffer payload) {
        byte[] data = new byte[payload.remaining()];
        payload.get(data);
        return data;
    }

    @Override
    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        //Синхронизация на уровне сессии (session) гарантирует, что все операции, связанные с одной сессией, выполняются последовательно.
        synchronized (session) {
            String deviceId = deviceSessionManager.getDeviceIdFromSession(session);
            if (deviceId == null) {
                MyLogger.logServer("Received message from unidentified session", true);
                return;
            }
            MyLogger.logServer("Начинаем приём бинарных сообщений от устройства [" + deviceId + "]", true);

            ByteBuffer payload = message.getPayload();
            payload.rewind();

            try {
                // Извлекаем requestId и данные
                String requestId = extractRequestId(payload);
                byte[] data = extractData(payload);

                MyLogger.logServer(requestId, true);

                // Синхронизация на уровне requestId гарантирует, что данные для одного requestId обрабатываются последовательно.
                synchronized (getBufferLock(requestId)) {
                    // Получаем или создаем буфер
                    ByteArrayOutputStream buffer = byteMessageBuffers.get(requestId);
                    if (buffer == null) {
                        buffer = new ByteArrayOutputStream();
                        byteMessageBuffers.put(requestId, buffer);
                    }

                    // Записываем данные в буфер
                    buffer.write(data);

                    // Если это последний фрагмент, обрабатываем сообщение
                    if (message.isLast()) {
                        byte[] fullMessageBytes = buffer.toByteArray();
                        byteMessageBuffers.remove(requestId);
                        bufferLocks.remove(requestId); // Удаляем монитор
                        MyLogger.logServer("Full binary response for requestId " + requestId + ", length: " + fullMessageBytes.length);
                        handleBinaryResponse(requestId, fullMessageBytes);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
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


    // найти все ключи содержащие diviceID т.к. id запросов содержат id устройств
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

    // найти все ключи содержащие diviceID т.к. id запросов содержат id устройств
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

        String deviceId = deviceSessionManager.getDeviceIdFromSession(session);

        //если это выход при существующем устройстве с таким же id то никаких объектов не создавалось в этой сессии поэтому тут делать нечего, просто выходим чтобы не ломать действующего подключения с таким же id устройства
        if (status.getCode()==4000) {
            MyLogger.logServer("deviseId ["+deviceId+"] неудачная попытка соединения с таким же id устройства");
            return;
        }


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
                        MyLogger.logServer("Completed text future for device " + deviceId + " due to connection close",true);
                        break;
                    }
                }

                for (String key : completeBinaryResponseFutures(deviceId)) {
                    binaryFuture = binaryResponseFutures.remove(key);
                    if (binaryFuture != null && !binaryFuture.isDone()) {
                        binaryFuture.completeExceptionally(new IOException("Connection closed"));
                        MyLogger.logServer("Completed binary future for device " + deviceId + " due to connection close",true);
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
        MyLogger.logServer("ждём текстовые данные для устройства requestId: " + requestId,true);
        responseFutures.put(requestId, future);
        return future;
    }

    public CompletableFuture<byte[]> waitForBinaryResponse(String requestId) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        MyLogger.logServer("ждём бинарные данные для устройства requestId: " + requestId,true);
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
    //        таким образом мы ассинхронно возвращаемся в точку вызова в класс ProxyController webSocketProxyHandler.waitForResponse(requestId);
            future.complete(response);
        } else {
            MyLogger.logServer("Unexpected response from device, requestId: " + requestId);
        }
    }




//    // при получении полного бинарного ответа выполнение  future.complete(response);
//    private void handleBinaryResponse(String requestId, byte[] data, String contentType) {
//
//        if (requestId == null) {
//            MyLogger.logServer("No requestId found in the binary response");
//            return;
//        }
//
//        // Ищем CompletableFuture по requestId
//        CompletableFuture<byte[]> future = binaryResponseFutures.remove(requestId);
//        if (future != null) {
//            MyLogger.logServer("Handling binary response for device, requestId: " + requestId);
//
//            //правильные заголовки в этой data есть? сейчас
//            future.complete(data);
//        } else {
//            MyLogger.logServer("Unexpected binary response from device, requestId: " + requestId);
//        }
//    }

    private void handleBinaryResponse(String requestId, byte[] data) {

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


//    private String getDeviceIdFromSession(WebSocketSession session) {
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



