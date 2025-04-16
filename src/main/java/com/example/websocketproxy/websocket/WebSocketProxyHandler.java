










package com.example.websocketproxy.websocket;

import com.example.websocketproxy.services.DeviceSessionManager;
import com.example.websocketproxy.services.logsandexceptions.MyLogger;
import com.example.websocketproxy.services.logsandexceptions.exceptions.DeviceWithThisIdIsInActiveSessionNow;
import com.example.websocketproxy.services.logsandexceptions.exceptions.MyOtherExceptions;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
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

    private final JsonSchema textMessageSchema;


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

        // Инициализация схемы JSON  // валидация получаемых данных
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        this.textMessageSchema = factory.getSchema("""
            {
              "type": "object",
              "required": ["requestId", "data", "isLast"],
              "properties": {
                "requestId": {"type": "string"},
                "data": {"type": "string"},
                "isLast": {"type": "boolean"}
              }
            }""");

    }


    // здесь мы общаемся с устройствами через вебсокет напрямую, ещё до первых запросов с контроллера
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        MyLogger.logServer("session.id = ["+session.getId()+"]");
        String deviceId = deviceSessionManager.getDeviceIdFromThisSession(session);
        if (deviceId == null) {
            session.close(CloseStatus.BAD_DATA);
            MyLogger.logServer("Connection rejected: missing or invalid deviceId");
            return;
        }

        // Проверка существующего подключения
        if (deviceSessionManager.isThisDeviceConnected(deviceId)) {
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




        deviceSessionManager.addDeviceWithSession(deviceId, session);
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

            // Преобразуем строку в JsonNode для валидации
            com.fasterxml.jackson.databind.JsonNode jsonNode =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);

            // Проверяем соответствие схеме
            Set<ValidationMessage> errors = textMessageSchema.validate(jsonNode);
            if (!errors.isEmpty()) {
                MyLogger.logServer("Invalid JSON format: " + errors);
                session.sendMessage(new TextMessage(
                        "Вы вероятно изменили клиентскую часть программы и она отправила JSON данные несоответствующие определённой схеме." + errors
                ));
                throw new MyOtherExceptions("полученные данные не соответствуют JSON схеме "+errors);
            }

            // Парсим JSON
            JsonObject json = new Gson().fromJson(payload, JsonObject.class);

            String requestId = json.get("requestId").getAsString();
            //пока не передаём в клиенте в  json этот параметр
//            String cookies = json.get("cookies").getAsString();

            // Валидация requestId на инъекции и допустимые символы
            if (!isValidRequestId(requestId)) {
                MyLogger.logServer("Invalid requestId format in text message: " + requestId, true);
                session.sendMessage(new TextMessage("{\"error\":\"Invalid requestId format\"}"));
                throw new MyOtherExceptions("Invalid requestId format in text message: " + requestId);
            }


            String data = json.get("data").getAsString();

            String isLast = json.get("isLast").getAsString();


            // Обработка данных получаем все части в буфер(в мапу), с ключом для каждого id запроса
            // Атомарная операция Добавит requestId с новым объектом SttringBuilder, если ключа requestId нет
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
            MyLogger.logServer("Error processing message: " + e.getMessage());
            try {
                session.sendMessage(new TextMessage("{\"error\":\"Error processing message\"}"));
            } catch (IOException ioe) {
                MyLogger.logServer("Failed to send error message: " + ioe.getMessage(), true);
            }
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


    /**
     * Проверяет requestId на соответствие допустимому формату.
     * Разрешены только буквы, цифры, дефис, подчеркивание и точка.
     */
    private boolean isValidRequestId(String requestId) {
        // Регулярное выражение для проверки допустимых символов
        // Разрешаем только буквы, цифры, дефис, подчеркивание | и точку
        String regex = "^[a-zA-Z0-9\\-_|\\.]+$";

        // Проверка на null или пустую строку
        if (requestId == null || requestId.isEmpty()) {
            return false;
        }

        // Проверка на максимальную длину (дополнительная проверка)
        if (requestId.length() > 255) {
            return false;
        }

        // Проверка на соответствие шаблону
        if (!requestId.matches(regex)) {
            return false;
        }

        // Проверка на наличие путевых последовательностей
        if (requestId.contains("..") || requestId.contains("/") || requestId.contains("\\")) {
            return false;
        }

        return true;
    }

    private String extractRequestId(ByteBuffer payload) {
        // Проверяем, достаточно ли данных в буфере
        if (payload.remaining() < 4) {
            MyLogger.logServer("Invalid binary message: insufficient data for requestId length", true);
            throw new IllegalArgumentException("Invalid binary message format: missing requestId length");
        }

        int requestIdLength = payload.getInt(); // Извлекаем 4 байта длины requestId

        // Проверяем на разумную длину requestId (предотвращение DoS)
        if (requestIdLength <= 0 || requestIdLength > 1024) { // Максимальная длина - 1KB
            MyLogger.logServer("Invalid requestIdLength: " + requestIdLength, true);
            throw new IllegalArgumentException("Invalid requestId length: must be between 1 and 1024 bytes");
        }

        // Проверяем, достаточно ли данных в буфере для извлечения requestId
        if (payload.remaining() < requestIdLength) {
            MyLogger.logServer("Invalid binary message: insufficient data for requestId content", true);
            throw new IllegalArgumentException("Invalid binary message format: incomplete requestId");
        }

        byte[] requestIdBytes = new byte[requestIdLength];
        payload.get(requestIdBytes);
        String requestId = new String(requestIdBytes, StandardCharsets.UTF_8);
        // Валидация requestId на инъекции и допустимые символы
        if (!isValidRequestId(requestId)) {
            MyLogger.logServer("Invalid requestId format: " + requestId, true);
            throw new IllegalArgumentException("Invalid requestId format: contains disallowed characters");
        }

        return requestId;

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
            String deviceId = deviceSessionManager.getDeviceIdFromThisSession(session);
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

        String deviceId = deviceSessionManager.getDeviceIdFromThisSession(session);

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
            deviceSessionManager.removeDeviceWithSession(deviceId);
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



