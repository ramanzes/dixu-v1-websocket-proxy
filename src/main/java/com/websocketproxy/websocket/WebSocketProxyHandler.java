//package com.websocketproxy.websocket;
//
//import com.websocketproxy.services.DeviceSessionManager;
//import com.websocketproxy.services.DeviceTokenService;
//import com.websocketproxy.services.logsandexceptions.MyLogger;
//import com.websocketproxy.services.logsandexceptions.exceptions.DeviceWithThisIdIsInActiveSessionNow;
//import com.websocketproxy.services.logsandexceptions.exceptions.MyOtherExceptions;
//import com.github.benmanes.caffeine.cache.Cache;
//import com.github.benmanes.caffeine.cache.Caffeine;
//import com.google.gson.Gson;
//import com.google.gson.JsonObject;
//import com.networknt.schema.JsonSchema;
//import com.networknt.schema.JsonSchemaFactory;
//import com.networknt.schema.SpecVersion.VersionFlag;
//import com.networknt.schema.ValidationMessage;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.JsonNode;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.HttpHeaders;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.CloseStatus;
//import org.springframework.web.socket.TextMessage;
//import org.springframework.web.socket.WebSocketSession;
//import org.springframework.web.socket.handler.BinaryWebSocketHandler;
//import org.springframework.web.socket.BinaryMessage;
//
//import javax.annotation.PostConstruct;
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.nio.ByteBuffer;
//import java.nio.charset.StandardCharsets;
//import java.security.MessageDigest;
//import java.util.*;
//import java.util.concurrent.*;
//import java.util.concurrent.locks.ReentrantLock;
//import java.util.regex.Pattern;
//import java.util.Set;
//
//@Component
//public class WebSocketProxyHandler extends BinaryWebSocketHandler {
//    private final DeviceSessionManager deviceSessionManager;
//    private final DeviceTokenService deviceTokenService;
//
//    // Замена ConcurrentHashMap на Caffeine Cache для повышения производительности
//    private final Cache<String, CompletableFuture<String>> responseFuturesCache;
//    private final Cache<String, CompletableFuture<byte[]>> binaryResponseFuturesCache;
//    private final Cache<String, StringBuilder> textMessageBuffersCache;
//    private final Cache<String, ByteArrayOutputStream> byteMessageBuffersCache;
//    private final Cache<String, ByteArrayOutputStream> headersThisResponseCache;
//
//    // Использование более гранулярной блокировки
//    private final ConcurrentHashMap<String, ReentrantLock> requestLocks = new ConcurrentHashMap<>();
//
//    // Планировщик для обработки тайм-аутов
//    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
//
//    // Схема для валидации JSON
//    private final JsonSchema textMessageSchema;
//
//    // Регулярное выражение для проверки валидности requestId
//    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_|\\.]+$");
//
//    // Конфигурируемые параметры
//    @Value("${websocket.future.timeout:30000}") // 30 секунд по умолчанию
//    private long futureTimeoutMillis;
//
//    @Value("${websocket.buffer.expiry:300}") // 5 минут по умолчанию
//    private long bufferExpirySeconds;
//
//    @Value("${websocket.requestId.maxLength:255}")
//    private int requestIdMaxLength;
//
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    public WebSocketProxyHandler(DeviceSessionManager deviceSessionManager, DeviceTokenService deviceTokenService) {
//        this.deviceSessionManager = deviceSessionManager;
//        this.deviceTokenService = deviceTokenService;
//
//        // Инициализация кэшей с TTL для автоматической очистки
//        this.responseFuturesCache = Caffeine.newBuilder()
//                .expireAfterWrite(5, TimeUnit.MINUTES)
//                .build();
//
//        this.binaryResponseFuturesCache = Caffeine.newBuilder()
//                .expireAfterWrite(5, TimeUnit.MINUTES)
//                .build();
//
//        this.textMessageBuffersCache = Caffeine.newBuilder()
//                .expireAfterWrite(5, TimeUnit.MINUTES)
//                .build();
//
//        this.byteMessageBuffersCache = Caffeine.newBuilder()
//                .expireAfterWrite(5, TimeUnit.MINUTES)
//                .build();
//
//        this.headersThisResponseCache = Caffeine.newBuilder()
//                .expireAfterWrite(5, TimeUnit.MINUTES)
//                .build();
//
//        // Инициализация схемы JSON
//        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(VersionFlag.V7);
//        textMessageSchema = factory.getSchema("""
//            {
//              "type": "object",
//              "required": ["requestId", "data", "isLast"],
//              "properties": {
//                "requestId": {"type": "string"},
//                "data": {"type": "string"},
//                "isLast": {"type": "boolean"}
//              }
//            }""");
//    }
//
//    @PostConstruct
//    public void init() {
//        // Запускаем планировщик для проверки и очистки просроченных фьючерсов
//        scheduler.scheduleAtFixedRate(this::checkTimeouts, 10, 10, TimeUnit.SECONDS);
//    }
//
//    /**
//     * Периодическая проверка и очистка "зависших" фьючерсов
//     */
//    @Scheduled(fixedDelay = 10000) // 10 секунд
//    private void checkTimeouts() {
//        long now = System.currentTimeMillis();
//
//        // Проверяем и очищаем просроченные фьючерсы
//        cleanupExpiredFutures(responseFuturesCache, now, "text");
//        cleanupExpiredFutures(binaryResponseFuturesCache, now, "binary");
//
//        // Логирование статистики
//        logCacheStats();
//    }
//
//    /**
//     * Очистка просроченных фьючерсов из кэша
//     */
//    private <T> void cleanupExpiredFutures(Cache<String, CompletableFuture<T>> cache, long now, String type) {
//        cache.asMap().forEach((requestId, future) -> {
//            if (!future.isDone() && isRequestTimedOut(requestId, now)) {
//                future.completeExceptionally(new TimeoutException("Request timed out after " + futureTimeoutMillis + " ms"));
//                cache.invalidate(requestId);
//                MyLogger.logServer("Completed " + type + " future for request " + requestId + " due to timeout", true);
//            }
//        });
//    }
//
//    /**
//     * Проверка, истек ли тайм-аут для запроса
//     */
//    private boolean isRequestTimedOut(String requestId, long now) {
//        // Здесь можно реализовать более сложную логику тайм-аутов,
//        // например, хранить время создания запроса
//        return true; // Для примера всегда возвращаем true
//    }
//
//    /**
//     * Логирование статистики кэша
//     */
//    private void logCacheStats() {
//        MyLogger.logServer("Cache stats - Text futures: " + responseFuturesCache.estimatedSize() +
//                ", Binary futures: " + binaryResponseFuturesCache.estimatedSize(), true);
//    }
//
//    // Здесь мы общаемся с устройствами через вебсокет напрямую, ещё до первых запросов с контроллера
//    @Override
//    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//        MyLogger.logServer("session.id = [" + session.getId() + "]");
//        String deviceId = deviceSessionManager.getDeviceIdFromThisSession(session);
//
//        // Extract authentication token from headers
//        String token = extractToken(session);
//
//
//
//
////
////
////
//        if (deviceId == null) {
//            session.close(CloseStatus.BAD_DATA);
//            MyLogger.logServer("Connection rejected: missing or invalid deviceId");
//            return;
//        }
//
//        // Проверка существующего подключения
//        if (deviceSessionManager.isThisDeviceConnected(deviceId)) {
//            // Закрываем соединение с специальным статусом
//            CloseStatus closeStatus = new CloseStatus(
//                    4000,
//                    "Device " + deviceId + " already connected"
//            );
//
//            // Отправка специального сообщения перед закрытием
//            try {
//                session.sendMessage(new TextMessage(
//                        "CONNECTION_REJECTED: Device already connected " +
//                                " Устройство с таким же ID уже зарегистрировано на прокси сервере. " +
//                                "Обратитесь в райсполком, где вам выдавали ID для вашего устройства"
//                ));
//            } catch (IOException e) {
//                MyLogger.logServer("Error sending rejection message: " + e.getMessage());
//            }
//
//            session.close(closeStatus);
//            throw new DeviceWithThisIdIsInActiveSessionNow();
//        }
//
//
////        if (deviceId != null) {
//            // Verify token before allowing connection
//            if (token != null && deviceTokenService.validateToken(deviceId, token)) {
//                deviceSessionManager.addDeviceWithSession(deviceId, session);
//                System.out.println("Authenticated device connected: " + deviceId);
//            } else {
//                // Authentication failed, close connection with authentication error
//                System.out.println("Authentication failed for device: " + deviceId);
//                session.sendMessage(new TextMessage(
//                        "CONNECTION_REJECTED: Неверный Токен для вашего устройства" +
//                                " Обратитесь в райсполком, где вы получили этот токен"
//                ));
//                session.close(new CloseStatus(4001, "Authentication failed"));
//            }
////        } else {
////            session.close(new CloseStatus(4002, "Device ID missing"));
////        }
//
////        deviceSessionManager.addDeviceWithSession(deviceId, session);
//
//        // Инициализация буферов для устройства (при необходимости)
//        MyLogger.logServer("Device connected: " + deviceId);
//    }
//
//    private String extractToken(WebSocketSession session) {
//        HttpHeaders headers = session.getHandshakeHeaders();
//        List<String> authHeaders = headers.get("Authorization");
//
//        if (authHeaders != null && !authHeaders.isEmpty()) {
//            String authHeader = authHeaders.get(0);
//            // Assuming "Bearer <token>" format
//            if (authHeader.startsWith("Bearer ")) {
//                return authHeader.substring(7);
//            }
//        }
//
//        // If no Authorization header, check for token in query params
//        String query = session.getUri().getQuery();
//        if (query != null && query.contains("token=")) {
//            String[] params = query.split("&");
//            for (String param : params) {
//                if (param.startsWith("token=")) {
//                    return param.substring(6);
//                }
//            }
//        }
//
//        return null;
//    }
//
//
//
//    // Когда сообщения по вебсокету приходят от устройства мы попадаем сюда
//    @Override
//    public void handleTextMessage(WebSocketSession session, TextMessage message) {
//        String payload = message.getPayload();
//        String truncatedPayload = payload.length() > 49 ?
//                payload.substring(0, 49) + "..." :
//                payload;
//        MyLogger.logServer(truncatedPayload, true);
//
//        try {
//            // Преобразуем строку в JsonNode для валидации
//            JsonNode jsonNode = objectMapper.readTree(payload);
//
//            // Проверяем соответствие схеме
//            Set<ValidationMessage> errors = textMessageSchema.validate(jsonNode);
//            if (!errors.isEmpty()) {
//                MyLogger.logServer("Invalid JSON format: " + errors);
//                sendErrorResponse(session, "Invalid JSON format");
//                return;
//            }
//
//            // Парсим JSON с помощью Gson
//            JsonObject json = new Gson().fromJson(payload, JsonObject.class);
//            String requestId = json.get("requestId").getAsString();
//
//            // Валидация requestId
//            if (!isValidRequestId(requestId)) {
//                MyLogger.logServer("Invalid requestId format in text message: " + requestId, true);
//                sendErrorResponse(session, "Invalid requestId format");
//                return;
//            }
//
//            String data = json.get("data").getAsString();
//            String isLast = json.get("isLast").getAsString();
//
//            // Безопасное получение и обновление буфера с использованием локов
//            ReentrantLock lock = requestLocks.computeIfAbsent(requestId, k -> new ReentrantLock());
//            lock.lock();
//            try {
//                // Получаем или создаем буфер
//                StringBuilder buffer = textMessageBuffersCache.get(requestId, k -> new StringBuilder());
//                buffer.append(data);
//
//                if ("true".equals(isLast)) {
//                    String fullMessage = buffer.toString();
//
//                    // Очищаем и удаляем буфер
//                    textMessageBuffersCache.invalidate(requestId);
//                    requestLocks.remove(requestId);
//
//                    MyLogger.logServer("Full response for requestId " + requestId + ": " + fullMessage);
//
//                    if (fullMessage.startsWith("HTTP/1.1")) {
//                        // Извлекаем заголовки для логирования
//                        String headers = "";
//                        int index = fullMessage.indexOf("\r\n\r\n");
//                        if (index != -1) {
//                            headers = fullMessage.substring(0, index);
//                            String[] header = headers.split("\r\n");
//                            for (String h : header) {
//                                if (h.contains(":")) {
//                                    String[] keyValue = h.split(":", 2);
//                                    MyLogger.logServer("!!!заголовки на отправку на прокси[" + keyValue[0].trim() + "]" +
//                                            "[" + keyValue[1].trim() + "]", true);
//                                }
//                            }
//                        } else {
//                            MyLogger.logServer("заголовки на отправку на прокси!!! неверный формат", true);
//                        }
//
//                        MyLogger.logServer("длина полного сообщения [" + requestId + "] " + fullMessage.length());
//                        MyLogger.logServer("длина заголовков этого сообщения [" + requestId + "] " + headers.length());
//
//                        handleResponse(requestId, fullMessage);
//                    } else {
//                        MyLogger.logServer("[нет вначале HTTP/1.1] Full message for requestId: " + requestId, true);
//                        throw new MyOtherExceptions("нет заголовка HTTP/1.1 у текстового типа данных");
//                    }
//                }
//            } finally {
//                lock.unlock();
//            }
//        } catch (Exception e) {
//            MyLogger.logServer("Error in handleTextMessage: " + e.getMessage(), true);
//            try {
//                sendErrorResponse(session, "Processing error");
//            } catch (Exception ex) {
//                MyLogger.logServer("Failed to send error response: " + ex.getMessage(), true);
//            }
//            e.printStackTrace();
//        }
//    }
//
//    /**
//     * Отправка сообщения об ошибке клиенту
//     */
//    private void sendErrorResponse(WebSocketSession session, String errorMessage) {
//        try {
//            session.sendMessage(new TextMessage("{\"error\":\"" + errorMessage + "\"}"));
//        } catch (IOException e) {
//            MyLogger.logServer("Failed to send error message: " + e.getMessage(), true);
//        }
//    }
//
//    @Override
//    public boolean supportsPartialMessages() {
//        return true; // Включаем поддержку фрагментированных сообщений
//    }
//
//    @Override
//    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
//        String deviceId = deviceSessionManager.getDeviceIdFromThisSession(session);
//        if (deviceId == null) {
//            MyLogger.logServer("Received message from unidentified session", true);
//            return;
//        }
//
//        MyLogger.logServer("Начинаем приём бинарных сообщений от устройства [" + deviceId + "]", true);
//
//        ByteBuffer payload = message.getPayload();
//        payload.rewind();
//
//        try {
//            // Извлекаем requestId и данные с проверкой безопасности
//            String requestId = extractRequestId(payload);
//
//            // Валидация requestId
//            if (!isValidRequestId(requestId)) {
//                MyLogger.logServer("Invalid requestId format in binary message: " + requestId, true);
//                sendErrorResponse(session, "Invalid requestId format");
//                return;
//            }
//
//            byte[] data = extractData(payload);
//
//            // Используем более эффективную блокировку на уровне requestId
//            ReentrantLock lock = requestLocks.computeIfAbsent(requestId, k -> new ReentrantLock());
//            lock.lock();
//            try {
//                // Получаем или создаем буфер
//                ByteArrayOutputStream buffer = byteMessageBuffersCache.get(requestId, k -> new ByteArrayOutputStream());
//                buffer.write(data);
//
//                // Если это последний фрагмент, обрабатываем сообщение
//                if (message.isLast()) {
//                    byte[] fullMessageBytes = buffer.toByteArray();
//
//                    // Очищаем ресурсы
//                    byteMessageBuffersCache.invalidate(requestId);
//                    requestLocks.remove(requestId);
//
//                    MyLogger.logServer("Full binary response for requestId " + requestId +
//                            ", length: " + fullMessageBytes.length);
//
//                    handleBinaryResponse(requestId, fullMessageBytes);
//                }
//            } finally {
//                lock.unlock();
//            }
//        } catch (Exception e) {
//            MyLogger.logServer("Error in handleBinaryMessage: " + e.getMessage(), true);
//            e.printStackTrace();
//        }
//    }
//
//    /**
//     * Безопасное извлечение requestId из бинарного сообщения
//     */
//    private String extractRequestId(ByteBuffer payload) {
//        // Проверяем, достаточно ли данных в буфере
//        if (payload.remaining() < 4) {
//            MyLogger.logServer("Invalid binary message: insufficient data for requestId length", true);
//            throw new IllegalArgumentException("Invalid binary message format: missing requestId length");
//        }
//
//        int requestIdLength = payload.getInt(); // Извлекаем 4 байта длины requestId
//
//        // Проверяем на разумную длину requestId (предотвращение DoS)
//        if (requestIdLength <= 0 || requestIdLength > requestIdMaxLength) {
//            MyLogger.logServer("Invalid requestIdLength: " + requestIdLength, true);
//            throw new IllegalArgumentException("Invalid requestId length: must be between 1 and " +
//                    requestIdMaxLength + " bytes");
//        }
//
//        // Проверяем, достаточно ли данных в буфере для извлечения requestId
//        if (payload.remaining() < requestIdLength) {
//            MyLogger.logServer("Invalid binary message: insufficient data for requestId content", true);
//            throw new IllegalArgumentException("Invalid binary message format: incomplete requestId");
//        }
//
//        byte[] requestIdBytes = new byte[requestIdLength];
//        payload.get(requestIdBytes);
//        return new String(requestIdBytes, StandardCharsets.UTF_8);
//    }
//
//    /**
//     * Извлечение данных из бинарного сообщения
//     */
//    private byte[] extractData(ByteBuffer payload) {
//        byte[] data = new byte[payload.remaining()];
//        payload.get(data);
//        return data;
//    }
//
//    /**
//     * Проверка валидности requestId
//     */
//    private boolean isValidRequestId(String requestId) {
//        // Проверка на null или пустую строку
//        if (requestId == null || requestId.isEmpty()) {
//            return false;
//        }
//
//        // Проверка на максимальную длину
//        if (requestId.length() > requestIdMaxLength) {
//            return false;
//        }
//
//        // Проверка на соответствие шаблону
//        if (!REQUEST_ID_PATTERN.matcher(requestId).matches()) {
//            return false;
//        }
//
//        // Проверка на наличие путевых последовательностей
//        return !requestId.contains("..") && !requestId.contains("/") && !requestId.contains("\\");
//    }
//
//    /**
//     * Вычисление хеша данных
//     */
//    private static String calculateHash(byte[] data) {
//        try {
//            MessageDigest digest = MessageDigest.getInstance("SHA-256");
//            byte[] hash = digest.digest(data);
//            return Base64.getEncoder().encodeToString(hash);
//        } catch (Exception e) {
//            return "Ошибка вычисления хэша";
//        }
//    }
//
//    /**
//     * Нахождение всех ключей, содержащих deviceId
//     */
//    private List<String> findKeysContainingDeviceId(Cache<String, ?> cache, String deviceId) {
//        List<String> keysToRemove = new ArrayList<>();
//        cache.asMap().keySet().forEach(key -> {
//            if (key.contains(deviceId)) {
//                keysToRemove.add(key);
//            }
//        });
//        return keysToRemove;
//    }
//
//    @Override
//    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
//        String deviceId = deviceSessionManager.getDeviceIdFromThisSession(session);
//
//        // Если это выход при существующем устройстве с таким же id, то просто выходим
//        if (status.getCode() == 4000) {
//            MyLogger.logServer("deviseId [" + deviceId + "] неудачная попытка соединения с таким же id устройства");
//            return;
//        }
//
//        if (deviceId != null) {
//            // Завершаем все CompletableFuture для этого устройства
//            clearDeviceResources(deviceId);
//
//            // Удаляем сессию устройства
//            deviceSessionManager.removeDeviceWithSession(deviceId);
//            MyLogger.logServer("Device disconnected: " + deviceId);
//        }
//    }
//
//    /**
//     * Очистка ресурсов для устройства при отключении
//     */
//    private void clearDeviceResources(String deviceId) {
//        // Очищаем буферы и future для устройства
//        clearCacheForDevice(responseFuturesCache, deviceId, "text future");
//        clearCacheForDevice(binaryResponseFuturesCache, deviceId, "binary future");
//        clearCacheForDevice(textMessageBuffersCache, deviceId, "text buffer");
//        clearCacheForDevice(byteMessageBuffersCache, deviceId, "byte buffer");
//        clearCacheForDevice(headersThisResponseCache, deviceId, "headers");
//
//        // Удаляем локи для устройства
//        new ArrayList<>(requestLocks.keySet()).stream()
//                .filter(key -> key.contains(deviceId))
//                .forEach(requestLocks::remove);
//    }
//
//    /**
//     * Очистка кэша для конкретного устройства
//     */
//    private <T> void clearCacheForDevice(Cache<String, T> cache, String deviceId, String resourceType) {
//        List<String> keys = findKeysContainingDeviceId(cache, deviceId);
//
//        for (String key : keys) {
//            // Для фьючерсов завершаем их с исключением
//            if (cache.getIfPresent(key) instanceof CompletableFuture) {
//                CompletableFuture<?> future = (CompletableFuture<?>) cache.getIfPresent(key);
//                if (future != null && !future.isDone()) {
//                    future.completeExceptionally(new IOException("Connection closed"));
//                }
//            }
//
//            cache.invalidate(key);
//            MyLogger.logServer("Cleared " + resourceType + " for key " + key, true);
//        }
//    }
//
//    /**
//     * Ожидание текстового ответа с таймаутом
//     */
//    public CompletableFuture<String> waitForResponse(String requestId) {
//        CompletableFuture<String> future = new CompletableFuture<>();
//
//        // Устанавливаем таймаут для фьючера
//        scheduleFutureTimeout(future, requestId);
//
//        MyLogger.logServer("ждём текстовые данные для устройства requestId: " + requestId, true);
//        responseFuturesCache.put(requestId, future);
//
//        return future;
//    }
//
//    /**
//     * Ожидание бинарного ответа с таймаутом
//     */
//    public CompletableFuture<byte[]> waitForBinaryResponse(String requestId) {
//        CompletableFuture<byte[]> future = new CompletableFuture<>();
//
//        // Устанавливаем таймаут для фьючера
//        scheduleFutureTimeout(future, requestId);
//
//        MyLogger.logServer("ждём бинарные данные для устройства requestId: " + requestId, true);
//        binaryResponseFuturesCache.put(requestId, future);
//
//        return future;
//    }
//
//    /**
//     * Установка таймаута для CompletableFuture
//     */
//    private <T> void scheduleFutureTimeout(CompletableFuture<T> future, String requestId) {
//        scheduler.schedule(() -> {
//            if (!future.isDone()) {
//                future.completeExceptionally(new TimeoutException("Request timed out after " + futureTimeoutMillis + " ms"));
//                MyLogger.logServer("Request timed out: " + requestId, true);
//            }
//        }, futureTimeoutMillis, TimeUnit.MILLISECONDS);
//    }
//
//    /**
//     * Обработка текстового ответа
//     */
//    private void handleResponse(String requestId, String response) throws MyLogger.CustomException {
//        if (requestId == null) {
//            MyLogger.logServer("No requestId found in the response");
//            throw new MyLogger.CustomException("получили сообщение для неизвестного request id", "request id null");
//        }
//
//        // Ищем CompletableFuture по requestId
//        CompletableFuture<String> future = responseFuturesCache.getIfPresent(requestId);
//        if (future != null) {
//            MyLogger.logServer("Handling response for device requestId: " + requestId);
//            responseFuturesCache.invalidate(requestId);
//            future.complete(response);
//        } else {
//            MyLogger.logServer("Unexpected response from device, requestId: " + requestId);
//        }
//    }
//


package com.websocketproxy.websocket;
import com.websocketproxy.services.DeviceSessionManager;
import com.websocketproxy.services.DeviceTokenService;
import com.websocketproxy.services.logsandexceptions.MyLogger;
import com.websocketproxy.services.logsandexceptions.exceptions.DeviceWithThisIdIsInActiveSessionNow;
import com.websocketproxy.services.logsandexceptions.exceptions.MyOtherExceptions;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.socket.BinaryMessage;
import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.Set;
@Component
public class WebSocketProxyHandler extends BinaryWebSocketHandler {
    private final DeviceSessionManager deviceSessionManager;
    private final DeviceTokenService deviceTokenService;
    // Замена ConcurrentHashMap на Caffeine Cache для повышения производительности
    private Cache<String, CompletableFuture<String>> responseFuturesCache;
    private Cache<String, CompletableFuture<byte[]>> binaryResponseFuturesCache;
    private Cache<String, StringBuilder> textMessageBuffersCache;
    private Cache<String, ByteArrayOutputStream> byteMessageBuffersCache;
    private Cache<String, ByteArrayOutputStream> headersThisResponseCache;
    // Использование более гранулярной блокировки
    private final ConcurrentHashMap<String, ReentrantLock> requestLocks = new ConcurrentHashMap<>();
    // Планировщик для обработки тайм-аутов
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    // Схема для валидации JSON
    private final JsonSchema textMessageSchema;
    // Регулярное выражение для проверки валидности requestId
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_|\\.]+$");
    // Конфигурируемые параметры
    @Value("${websocket.future.timeout:30000}") // 30 секунд по умолчанию
    private long futureTimeoutMillis;
    @Value("${websocket.buffer.expiry:300}") // 5 минут по умолчанию
    private long bufferExpirySeconds;
    @Value("${websocket.requestId.maxLength:255}")
    private int requestIdMaxLength;
    @Value("${websocket.debug.mode:true}") // Новый параметр для режима отладки
    private boolean debugMode;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebSocketProxyHandler(DeviceSessionManager deviceSessionManager, DeviceTokenService deviceTokenService) {
        this.deviceSessionManager = deviceSessionManager;
        this.deviceTokenService = deviceTokenService;

        // Инициализация схемы JSON (не зависит от кэшей)
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(VersionFlag.V7);
        textMessageSchema = factory.getSchema("""
            {
              "type": "object",
              "required": ["requestId", "data", "isLast"],
              "properties": {
                "requestId": {"type": "stringn"},
                "data": {"type": "string"},
                "isLast": {"type": "boolean"}
              }
            }""");
    }

    @PostConstruct
    public void init() {
        // Инициализация кэшей с учетом режима отладки
        initializeCaches();

        // Запускаем планировщик для проверки и очистки просроченных фьючерсов
        // (только если не в режиме отладки)
        if (!debugMode) {
            scheduler.scheduleAtFixedRate(this::checkTimeouts, 10, 10, TimeUnit.SECONDS);
        } else {
            MyLogger.logServer("Debug mode enabled: infinite TTL for all caches and requests", true);
        }
    }

    /**
     * Инициализация кэшей с учетом режима отладки
     */
    private void initializeCaches() {
        if (debugMode) {
            // В режиме отладки создаем кэши без TTL
            this.responseFuturesCache = Caffeine.newBuilder().build();
            this.binaryResponseFuturesCache = Caffeine.newBuilder().build();
            this.textMessageBuffersCache = Caffeine.newBuilder().build();
            this.byteMessageBuffersCache = Caffeine.newBuilder().build();
            this.headersThisResponseCache = Caffeine.newBuilder().build();
        } else {
            // Стандартное поведение с TTL
            this.responseFuturesCache = Caffeine.newBuilder()
                    .expireAfterWrite(bufferExpirySeconds, TimeUnit.SECONDS)
                    .build();
            this.binaryResponseFuturesCache = Caffeine.newBuilder()
                    .expireAfterWrite(bufferExpirySeconds, TimeUnit.SECONDS)
                    .build();
            this.textMessageBuffersCache = Caffeine.newBuilder()
                    .expireAfterWrite(bufferExpirySeconds, TimeUnit.SECONDS)
                    .build();
            this.byteMessageBuffersCache = Caffeine.newBuilder()
                    .expireAfterWrite(bufferExpirySeconds, TimeUnit.SECONDS)
                    .build();
            this.headersThisResponseCache = Caffeine.newBuilder()
                    .expireAfterWrite(bufferExpirySeconds, TimeUnit.SECONDS)
                    .build();
        }
    }

    /**
     * Динамическое переключение режима отладки
     *
     * @param enabled true для включения режима отладки, false для отключения
     */
    public void setDebugMode(boolean enabled) {
        if (this.debugMode != enabled) {
            this.debugMode = enabled;
            // Пересоздаем кэши с новыми настройками
            initializeCaches();
            MyLogger.logServer("Debug mode " + (enabled ? "enabled" : "disabled") +
                    ": " + (enabled ? "infinite" : bufferExpirySeconds + " seconds") +
                    " TTL for all caches", true);
        }
    }

    /**
     * Получение текущего состояния режима отладки
     *
     * @return true если режим отладки включен, иначе false
     */
    public boolean isDebugMode() {
        return debugMode;
    }

    /**
     * Периодическая проверка и очистка "зависших" фьючерсов
     */
    @Scheduled(fixedDelay = 10000) // 10 секунд
    private void checkTimeouts() {
        // В режиме отладки пропускаем проверку тайм-аутов
        if (debugMode) {
            return;
        }

        long now = System.currentTimeMillis();
        // Проверяем и очищаем просроченные фьючерсы
        cleanupExpiredFutures(responseFuturesCache, now, "text");
        cleanupExpiredFutures(binaryResponseFuturesCache, now, "binary");
        // Логирование статистики
        logCacheStats();
    }

    /**
     * Очистка просроченных фьючерсов из кэша
     */
    private <T> void cleanupExpiredFutures(Cache<String, CompletableFuture<T>> cache, long now, String type) {
        cache.asMap().forEach((requestId, future) -> {
            if (!future.isDone() && isRequestTimedOut(requestId, now)) {
                future.completeExceptionally(new TimeoutException("Request timed out after " + futureTimeoutMillis + " ms"));
                cache.invalidate(requestId);
                MyLogger.logServer("Completed " + type + " future for request " + requestId + " due to timeout", true);
            }
        });
    }

    /**
     * Проверка, истек ли тайм-аут для запроса
     */
    private boolean isRequestTimedOut(String requestId, long now) {
        // В режиме отладки запросы никогда не истекают
        if (debugMode) {
            return false;
        }

        // Здесь можно реализовать более сложную логику тайм-аутов,
        // например, хранить время создания запроса
        return true; // Для примера всегда возвращаем true
    }

    /**
     * Логирование статистики кэша
     */
    private void logCacheStats() {
        MyLogger.logServer("Cache stats - Text futures: " + responseFuturesCache.estimatedSize() +
                ", Binary futures: " + binaryResponseFuturesCache.estimatedSize() +
                ", Debug mode: " + (debugMode ? "ON" : "OFF"), true);
    }

    // Здесь мы общаемся с устройствами через вебсокет напрямую, ещё до первых запросов с контроллера
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        MyLogger.logServer("session.id = [" + session.getId() + "]");
        String deviceId = deviceSessionManager.getDeviceIdFromThisSession(session);
        // Extract authentication token from headers
        String token = extractToken(session);

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
                        "CONNECTION_REJECTED: Device already connected " +
                                " Устройство с таким же ID уже зарегистрировано на прокси сервере. " +
                                "Обратитесь в райсполком, где вам выдавали ID для вашего устройства"
                ));
            } catch (IOException e) {
                MyLogger.logServer("Error sending rejection message: " + e.getMessage());
            }
            session.close(closeStatus);
            throw new DeviceWithThisIdIsInActiveSessionNow();
        }

        // Verify token before allowing connection
        if (token != null && deviceTokenService.validateToken(deviceId, token)) {
            deviceSessionManager.addDeviceWithSession(deviceId, session);
            System.out.println("Authenticated device connected: " + deviceId);
        } else {
            // Authentication failed, close connection with authentication error
            System.out.println("Authentication failed for device: " + deviceId);
            session.sendMessage(new TextMessage(
                    "CONNECTION_REJECTED: Неверный Токен для вашего устройства" +
                            " Обратитесь в райсполком, где вы получили этот токен"
            ));
            session.close(new CloseStatus(4001, "Authentication failed"));
        }

        // Инициализация буферов для устройства (при необходимости)
        MyLogger.logServer("Device connected: " + deviceId);
    }

    private String extractToken(WebSocketSession session) {
        HttpHeaders headers = session.getHandshakeHeaders();
        List<String> authHeaders = headers.get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.get(0);
            // Assuming "Bearer <token>" format
            if (authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }
        // If no Authorization header, check for token in query params
        String query = session.getUri().getQuery();
        if (query != null && query.contains("token=")) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("token=")) {
                    return param.substring(6);
                }
            }
        }
        return null;
    }

    // Когда сообщения по вебсокету приходят от устройства мы попадаем сюда
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        String truncatedPayload = payload.length() > 49 ?
                payload.substring(0, 49) + "..." :
                payload;
        MyLogger.logServer(truncatedPayload, true);
        try {
            // Преобразуем строку в JsonNode для валидации
            JsonNode jsonNode = objectMapper.readTree(payload);
            // Проверяем соответствие схеме
            Set<ValidationMessage> errors = textMessageSchema.validate(jsonNode);
            if (!errors.isEmpty()) {
                MyLogger.logServer("Invalid JSON format: " + errors);
                sendErrorResponse(session, "Invalid JSON format");
                return;
            }
            // Парсим JSON с помощью Gson
            JsonObject json = new Gson().fromJson(payload, JsonObject.class);
            String requestId = json.get("requestId").getAsString();
            // Валидация requestId
            if (!isValidRequestId(requestId)) {
                MyLogger.logServer("Invalid requestId format in text message: " + requestId, true);
                sendErrorResponse(session, "Invalid requestId format");
                return;
            }
            String data = json.get("data").getAsString();
            String isLast = json.get("isLast").getAsString();
            // Безопасное получение и обновление буфера с использованием локов
            ReentrantLock lock = requestLocks.computeIfAbsent(requestId, k -> new ReentrantLock());
            lock.lock();
            try {
                // Получаем или создаем буфер
                StringBuilder buffer = textMessageBuffersCache.get(requestId, k -> new StringBuilder());
                buffer.append(data);
                if ("true".equals(isLast)) {
                    String fullMessage = buffer.toString();
                    // Очищаем и удаляем буфер
                    textMessageBuffersCache.invalidate(requestId);
                    requestLocks.remove(requestId);
                    MyLogger.logServer("Full response for requestId " + requestId + ": " + fullMessage);
                    if (fullMessage.startsWith("HTTP/1.1")) {
                        // Извлекаем заголовки для логирования
                        String headers = "";
                        int index = fullMessage.indexOf("\r\n\r\n");
                        if (index != -1) {
                            headers = fullMessage.substring(0, index);
                            String[] header = headers.split("\r\n");
                            for (String h : header) {
                                if (h.contains(":")) {
                                    String[] keyValue = h.split(":", 2);
                                    MyLogger.logServer("!!!заголовки на отправку на прокси[" + keyValue[0].trim() + "]" +
                                            "[" + keyValue[1].trim() + "]", true);
                                }
                            }
                        } else {
                            MyLogger.logServer("заголовки на отправку на прокси!!! неверный формат", true);
                        }
                        MyLogger.logServer("длина полного сообщения [" + requestId + "] " + fullMessage.length());
                        MyLogger.logServer("длина заголовков этого сообщения [" + requestId + "] " + headers.length());
                        handleResponse(requestId, fullMessage);
                    } else {
                        MyLogger.logServer("[нет вначале HTTP/1.1] Full message for requestId: " + requestId, true);
                        throw new MyOtherExceptions("нет заголовка HTTP/1.1 у текстового типа данных");
                    }
                }
            } finally {
                lock.unlock();
            }
        } catch (Exception e) {
            MyLogger.logServer("Error in handleTextMessage: " + e.getMessage(), true);
            try {
                sendErrorResponse(session, "Processing error");
            } catch (Exception ex) {
                MyLogger.logServer("Failed to send error response: " + ex.getMessage(), true);
            }
            e.printStackTrace();
        }
    }

    /**
     * Отправка сообщения об ошибке клиенту
     */
    private void sendErrorResponse(WebSocketSession session, String errorMessage) {
        try {
            session.sendMessage(new TextMessage("{\"error\":\"" + errorMessage + "\"}"));
        } catch (IOException e) {
            MyLogger.logServer("Failed to send error message: " + e.getMessage(), true);
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return true; // Включаем поддержку фрагментированных сообщений
    }

    @Override
    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String deviceId = deviceSessionManager.getDeviceIdFromThisSession(session);
        if (deviceId == null) {
            MyLogger.logServer("Received message from unidentified session", true);
            return;
        }
        MyLogger.logServer("Начинаем приём бинарных сообщений от устройства [" + deviceId + "]", true);
        ByteBuffer payload = message.getPayload();
        payload.rewind();
        try {
            // Извлекаем requestId и данные с проверкой безопасности
            String requestId = extractRequestId(payload);
            // Валидация requestId
            if (!isValidRequestId(requestId)) {
                MyLogger.logServer("Invalid requestId format in binary message: " + requestId, true);
                sendErrorResponse(session, "Invalid requestId format");
                return;
            }
            byte[] data = extractData(payload);
            // Используем более эффективную блокировку на уровне requestId
            ReentrantLock lock = requestLocks.computeIfAbsent(requestId, k -> new ReentrantLock());
            lock.lock();
            try {
                // Получаем или создаем буфер
                ByteArrayOutputStream buffer = byteMessageBuffersCache.get(requestId, k -> new ByteArrayOutputStream());
                buffer.write(data);
                // Если это последний фрагмент, обрабатываем сообщение
                if (message.isLast()) {
                    byte[] fullMessageBytes = buffer.toByteArray();
                    // Очищаем ресурсы
                    byteMessageBuffersCache.invalidate(requestId);
                    requestLocks.remove(requestId);
                    MyLogger.logServer("Full binary response for requestId " + requestId +
                            ", length: " + fullMessageBytes.length);
                    handleBinaryResponse(requestId, fullMessageBytes);
                }
            } finally {
                lock.unlock();
            }
        } catch (Exception e) {
            MyLogger.logServer("Error in handleBinaryMessage: " + e.getMessage(), true);
            e.printStackTrace();
        }
    }

    /**
     * Безопасное извлечение requestId из бинарного сообщения
     */
    private String extractRequestId(ByteBuffer payload) {
        // Проверяем, достаточно ли данных в буфере
        if (payload.remaining() < 4) {
            MyLogger.logServer("Invalid binary message: insufficient data for requestId length", true);
            throw new IllegalArgumentException("Invalid binary message format: missing requestId length");
        }
        int requestIdLength = payload.getInt(); // Извлекаем 4 байта длины requestId
        // Проверяем на разумную длину requestId (предотвращение DoS)
        if (requestIdLength <= 0 || requestIdLength > requestIdMaxLength) {
            MyLogger.logServer("Invalid requestIdLength: " + requestIdLength, true);
            throw new IllegalArgumentException("Invalid requestId length: must be between 1 and " +
                    requestIdMaxLength + " bytes");
        }
        // Проверяем, достаточно ли данных в буфере для извлечения requestId
        if (payload.remaining() < requestIdLength) {
            MyLogger.logServer("Invalid binary message: insufficient data for requestId content", true);
            throw new IllegalArgumentException("Invalid binary message format: incomplete requestId");
        }
        byte[] requestIdBytes = new byte[requestIdLength];
        payload.get(requestIdBytes);
        return new String(requestIdBytes, StandardCharsets.UTF_8);
    }

    /**
     * Извлечение данных из бинарного сообщения
     */
    private byte[] extractData(ByteBuffer payload) {
        byte[] data = new byte[payload.remaining()];
        payload.get(data);
        return data;
    }

    /**
     * Проверка валидности requestId
     */
    private boolean isValidRequestId(String requestId) {
        // Проверка на null или пустую строку
        if (requestId == null || requestId.isEmpty()) {
            return false;
        }
        // Проверка на максимальную длину
        if (requestId.length() > requestIdMaxLength) {
            return false;
        }
        // Проверка на соответствие шаблону
        if (!REQUEST_ID_PATTERN.matcher(requestId).matches()) {
            return false;
        }
        // Проверка на наличие путевых последовательностей
        return !requestId.contains("..") && !requestId.contains("/") && !requestId.contains("\\");
    }

    /**
     * Вычисление хеша данных
     */
    private static String calculateHash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "Ошибка вычисления хэша";
        }
    }

    /**
     * Нахождение всех ключей, содержащих deviceId
     */
    private List<String> findKeysContainingDeviceId(Cache<String, ?> cache, String deviceId) {
        List<String> keysToRemove = new ArrayList<>();
        cache.asMap().keySet().forEach(key -> {
            if (key.contains(deviceId)) {
                keysToRemove.add(key);
            }
        });
        return keysToRemove;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String deviceId = deviceSessionManager.getDeviceIdFromThisSession(session);
        // Если это выход при существующем устройстве с таким же id, то просто выходим
        if (status.getCode() == 4000) {
            MyLogger.logServer("deviseId [" + deviceId + "] неудачная попытка соединения с таким же id устройства");
            return;
        }
        if (deviceId != null) {
            // Завершаем все CompletableFuture для этого устройства
            clearDeviceResources(deviceId);
            // Удаляем сессию устройства
            deviceSessionManager.removeDeviceWithSession(deviceId);
            MyLogger.logServer("Device disconnected: " + deviceId);
        }
    }

    /**
     * Очистка ресурсов для устройства при отключении
     */
    private void clearDeviceResources(String deviceId) {
        // Очищаем буферы и future для устройства
        clearCacheForDevice(responseFuturesCache, deviceId, "text future");
        clearCacheForDevice(binaryResponseFuturesCache, deviceId, "binary future");
        clearCacheForDevice(textMessageBuffersCache, deviceId, "text buffer");
        clearCacheForDevice(byteMessageBuffersCache, deviceId, "byte buffer");
        clearCacheForDevice(headersThisResponseCache, deviceId, "headers");
        // Удаляем локи для устройства
        new ArrayList<>(requestLocks.keySet()).stream()
                .filter(key -> key.contains(deviceId))
                .forEach(requestLocks::remove);
    }

    /**
     * Очистка кэша для конкретного устройства
     */
    private <T> void clearCacheForDevice(Cache<String, T> cache, String deviceId, String resourceType) {
        List<String> keys = findKeysContainingDeviceId(cache, deviceId);
        for (String key : keys) {
            // Для фьючерсов завершаем их с исключением
            if (cache.getIfPresent(key) instanceof CompletableFuture) {
                CompletableFuture<?> future = (CompletableFuture<?>) cache.getIfPresent(key);
                if (future != null && !future.isDone()) {
                    future.completeExceptionally(new IOException("Connection closed"));
                }
            }
            cache.invalidate(key);
            MyLogger.logServer("Cleared " + resourceType + " for key " + key, true);
        }
    }

    /**
     * Ожидание текстового ответа с таймаутом
     */
    public CompletableFuture<String> waitForResponse(String requestId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        // Устанавливаем таймаут для фьючера только если не в режиме отладки
        if (!debugMode) {
            scheduleFutureTimeout(future, requestId);
        }
        MyLogger.logServer("ждём текстовые данные для устройства requestId: " + requestId +
                (debugMode ? " (в режиме отладки без таймаута)" : ""), true);
        responseFuturesCache.put(requestId, future);
        return future;
    }

    /**
     * Ожидание бинарного ответа с таймаутом
     */
    public CompletableFuture<byte[]> waitForBinaryResponse(String requestId) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        // Устанавливаем таймаут для фьючера только если не в режиме отладки
        if (!debugMode) {
            scheduleFutureTimeout(future, requestId);
        }
        MyLogger.logServer("ждём бинарные данные для устройства requestId: " + requestId +
                (debugMode ? " (в режиме отладки без таймаута)" : ""), true);
        binaryResponseFuturesCache.put(requestId, future);
        return future;
    }

    /**
     * Установка таймаута для CompletableFuture
     */
    private <T> void scheduleFutureTimeout(CompletableFuture<T> future, String requestId) {
        scheduler.schedule(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("Request timed out after " + futureTimeoutMillis + " ms"));
                MyLogger.logServer("Request timed out: " + requestId, true);
            }
        }, futureTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Обработка текстового ответа
     */
    private void handleResponse(String requestId, String response) throws MyLogger.CustomException {
        if (requestId == null) {
            MyLogger.logServer("No requestId found in the response");
            throw new MyLogger.CustomException("получили сообщение для неизвестного request id", "request id null");
        }
        // Ищем CompletableFuture по requestId
        CompletableFuture<String> future = responseFuturesCache.getIfPresent(requestId);
        if (future != null) {
            MyLogger.logServer("Handling response for device requestId: " + requestId);
            responseFuturesCache.invalidate(requestId);
            future.complete(response);
        } else {
            MyLogger.logServer("Unexpected response from device, requestId: " + requestId);
        }
    }

    /**
     * Обработка бинарного ответа
     */
    private void handleBinaryResponse(String requestId, byte[] data) {
        if (requestId == null) {
            MyLogger.logServer("No requestId found in the binary response");
            return;
        }

        // Ищем CompletableFuture по requestId
        CompletableFuture<byte[]> future = binaryResponseFuturesCache.getIfPresent(requestId);
        if (future != null) {
            MyLogger.logServer("Handling binary response for device, requestId: " + requestId);
            binaryResponseFuturesCache.invalidate(requestId);
            future.complete(data);
        } else {
            MyLogger.logServer("Unexpected binary response from device, requestId: " + requestId);
        }
    }

    /**
     * Метод для остановки внутренних ресурсов при завершении работы приложения
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}








//
//
//package com.example.websocketproxy.websocket;
//
//import com.example.websocketproxy.services.DeviceSessionManager;
//import com.example.websocketproxy.services.logsandexceptions.MyLogger;
//import com.example.websocketproxy.services.logsandexceptions.exceptions.DeviceWithThisIdIsInActiveSessionNow;
//import com.example.websocketproxy.services.logsandexceptions.exceptions.MyOtherExceptions;
//import com.google.gson.Gson;
//import com.google.gson.JsonObject;
//import com.networknt.schema.JsonSchema;
//import com.networknt.schema.JsonSchemaFactory;
//import com.networknt.schema.SpecVersion;
//import com.networknt.schema.ValidationMessage;
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.CloseStatus;
//import org.springframework.web.socket.TextMessage;
//import org.springframework.web.socket.WebSocketSession;
//import org.springframework.web.socket.handler.BinaryWebSocketHandler;
//import org.springframework.web.socket.BinaryMessage;
//
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.nio.ByteBuffer;
//import java.nio.charset.StandardCharsets;
//import java.security.MessageDigest;
//import java.util.*;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ConcurrentHashMap;
//
//@Component
////public class WebSocketProxyHandler extends TextWebSocketHandler  {
//public class WebSocketProxyHandler extends BinaryWebSocketHandler {
//
//    private final JsonSchema textMessageSchema;
//
//
//    private final DeviceSessionManager deviceSessionManager;
//    private final Map<String, CompletableFuture<String>> responseFutures = new ConcurrentHashMap<>();
//    private final Map<String, CompletableFuture<byte[]>> binaryResponseFutures = new ConcurrentHashMap<>();
//
//    //!!!!!!!!!!!!!буферы должны быть не для id устройства, а для id каждого запроса этого устройства
//
//    private final Map<String, StringBuilder> textMessageBuffers = new ConcurrentHashMap<>(); // Буфер для фрагментированных сообщений
//    private final Map<String, ByteArrayOutputStream> byteMessageBuffers = new ConcurrentHashMap<>();
//
//    private final Map<String, ByteArrayOutputStream> headersThisResponse = new ConcurrentHashMap<>();
//
//  //  private final Map<String, String> lastContentTypes = new ConcurrentHashMap<>();
//
//    public WebSocketProxyHandler(DeviceSessionManager deviceSessionManager) {
//        this.deviceSessionManager = deviceSessionManager;
//
//        // Инициализация схемы JSON  // валидация получаемых данных
//        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
//        this.textMessageSchema = factory.getSchema("""
//            {
//              "type": "object",
//              "required": ["requestId", "data", "isLast"],
//              "properties": {
//                "requestId": {"type": "string"},
//                "data": {"type": "string"},
//                "isLast": {"type": "boolean"}
//              }
//            }""");
//
//    }
//
//
//    // здесь мы общаемся с устройствами через вебсокет напрямую, ещё до первых запросов с контроллера
//    @Override
//    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//        MyLogger.logServer("session.id = ["+session.getId()+"]");
//        String deviceId = deviceSessionManager.getDeviceIdFromThisSession(session);
//        if (deviceId == null) {
//            session.close(CloseStatus.BAD_DATA);
//            MyLogger.logServer("Connection rejected: missing or invalid deviceId");
//            return;
//        }
//
//        // Проверка существующего подключения
//        if (deviceSessionManager.isThisDeviceConnected(deviceId)) {
//            // Закрываем соединение с специальным статусом
//            CloseStatus closeStatus = new CloseStatus(
//                    4000,
//                    "Device " + deviceId + " already connected"
//            );
//
//            // Отправка специального сообщения перед закрытием
//            try {
//                session.sendMessage(new TextMessage(
//                        "CONNECTION_REJECTED: Device already connected "+" Устройство с таким же ID уже зарегистрировано на прокси сервере. Обратитесь в райсполком, где вам выдавали ID для вашего устройства"
//                ));
//            } catch (IOException e) {
//                MyLogger.logServer("Error sending rejection message: " + e.getMessage());
//            }
//
//            session.close(closeStatus);
//            throw new DeviceWithThisIdIsInActiveSessionNow();
//        }
//
//
//
//
//        deviceSessionManager.addDeviceWithSession(deviceId, session);
//        textMessageBuffers.put(deviceId, new StringBuilder()); // Инициализация буфера для устройства
//        byteMessageBuffers.put(deviceId, new ByteArrayOutputStream()); // Инициализация буфера для устройства
//        MyLogger.logServer("Device connected: " + deviceId);
//    }
//
//
//
//// когда сообщения по вебсокету приходят от устройства мы попадаем сюда
//    @Override
//    public void handleTextMessage(WebSocketSession session, TextMessage message) {
//
//        String payload = message.getPayload();
//
//        MyLogger.logServer(payload.substring(0,49)+"...",true);
//        try {
//
//            // Преобразуем строку в JsonNode для валидации
//            com.fasterxml.jackson.databind.JsonNode jsonNode =
//                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
//
//            // Проверяем соответствие схеме
//            Set<ValidationMessage> errors = textMessageSchema.validate(jsonNode);
//            if (!errors.isEmpty()) {
//                MyLogger.logServer("Invalid JSON format: " + errors);
//                session.sendMessage(new TextMessage(
//                        "Вы вероятно изменили клиентскую часть программы и она отправила JSON данные несоответствующие определённой схеме." + errors
//                ));
//                throw new MyOtherExceptions("полученные данные не соответствуют JSON схеме "+errors);
//            }
//
//            // Парсим JSON
//            JsonObject json = new Gson().fromJson(payload, JsonObject.class);
//
//            String requestId = json.get("requestId").getAsString();
//            //пока не передаём в клиенте в  json этот параметр
////            String cookies = json.get("cookies").getAsString();
//
//            // Валидация requestId на инъекции и допустимые символы
//            if (!isValidRequestId(requestId)) {
//                MyLogger.logServer("Invalid requestId format in text message: " + requestId, true);
//                session.sendMessage(new TextMessage("{\"error\":\"Invalid requestId format\"}"));
//                throw new MyOtherExceptions("Invalid requestId format in text message: " + requestId);
//            }
//
//
//            String data = json.get("data").getAsString();
//
//            String isLast = json.get("isLast").getAsString();
//
//
//            // Обработка данных получаем все части в буфер(в мапу), с ключом для каждого id запроса
//            // Атомарная операция Добавит requestId с новым объектом SttringBuilder, если ключа requestId нет
//            StringBuilder buffer = textMessageBuffers.computeIfAbsent(requestId, k -> new StringBuilder());
//            buffer.append(data);
//
//            if (isLast.equals("true")) {
//                String fullMessage = buffer.toString();
//                buffer.setLength(0); // Очищаем буфер
//                textMessageBuffers.remove(requestId); // Удаляем буфер для requestId
//
////                MyLogger.logServer(fullMessage,true);
//                MyLogger.logServer("Full response for requestId " + requestId + ": " + fullMessage);
////                // Если сообщение слишком большое, возможно, нужно добавить дополнительную обработку
////                if (fullMessage.length() > MAX_MESSAGE_SIZE) {
////                    MyLogger.logServer("Сообщение для requestId: " + requestId + " слишком большое, обрабатываем по частям.");
////                    // Можно добавить логику для обработки слишком больших сообщений
////                }
////
//                if (fullMessage.startsWith("HTTP/1.1")) {
////                    handleResponse(requestId, fullMessage, cookies);
//                    // Извлекаем заголовки для логирования
//                    String headers = "";
//                    int index = fullMessage.indexOf("\r\n\r\n");
//                    if (index != -1) {
//                        headers = fullMessage.substring(0, index);
//
//                    String[] header = headers.split("\r\n");
//                    for (String h : header) {
//                        if (h.contains(":")) {
//                            String[] keyValue = h.split(":", 2);
//                            MyLogger.logServer("!!!заголовки на отправку на прокси["+keyValue[0].trim()+"]"+"["+keyValue[1].trim()+"]",true);
//                        }
//                    }
//                    } else {
//                        MyLogger.logServer("заголовки на отправку на прокси!!! неверный формат",true);
//                    }
//
//                    MyLogger.logServer("длина полного сообщения ["+requestId+"] "+fullMessage.length());
//                    MyLogger.logServer("длина заголовков этого сообщения ["+requestId+"] "+headers.length());
//
//                    handleResponse(requestId, fullMessage);
//                } else {
//                    MyLogger.logServer("[нет вначале HTTP/1.1] Full message for requestId: " + requestId,true);
//                    throw new MyOtherExceptions("нет заголовка HTTP/1.1 у текстового типа данных");
//                }
//            }
//        } catch (Exception e) {
//            MyLogger.logServer("Error processing message: " + e.getMessage());
//            try {
//                session.sendMessage(new TextMessage("{\"error\":\"Error processing message\"}"));
//            } catch (IOException ioe) {
//                MyLogger.logServer("Failed to send error message: " + ioe.getMessage(), true);
//            }
//            e.printStackTrace();
//        }
//    }
//
//    @Override
//    public boolean supportsPartialMessages() {
//        return true; // Включаем поддержку фрагментированных сообщений
//    }
//
//
//
//    private final Map<String, Object> bufferLocks = new ConcurrentHashMap<>();
//
//    private Object getBufferLock(String requestId) {
//        return bufferLocks.computeIfAbsent(requestId, k -> new Object());
//    }
//
//
//    /**
//     * Проверяет requestId на соответствие допустимому формату.
//     * Разрешены только буквы, цифры, дефис, подчеркивание и точка.
//     */
//    private boolean isValidRequestId(String requestId) {
//        // Регулярное выражение для проверки допустимых символов
//        // Разрешаем только буквы, цифры, дефис, подчеркивание | и точку
//        String regex = "^[a-zA-Z0-9\\-_|\\.]+$";
//
//        // Проверка на null или пустую строку
//        if (requestId == null || requestId.isEmpty()) {
//            return false;
//        }
//
//        // Проверка на максимальную длину (дополнительная проверка)
//        if (requestId.length() > 255) {
//            return false;
//        }
//
//        // Проверка на соответствие шаблону
//        if (!requestId.matches(regex)) {
//            return false;
//        }
//
//        // Проверка на наличие путевых последовательностей
//        if (requestId.contains("..") || requestId.contains("/") || requestId.contains("\\")) {
//            return false;
//        }
//
//        return true;
//    }
//
//    private String extractRequestId(ByteBuffer payload) {
//        // Проверяем, достаточно ли данных в буфере
//        if (payload.remaining() < 4) {
//            MyLogger.logServer("Invalid binary message: insufficient data for requestId length", true);
//            throw new IllegalArgumentException("Invalid binary message format: missing requestId length");
//        }
//
//        int requestIdLength = payload.getInt(); // Извлекаем 4 байта длины requestId
//
//        // Проверяем на разумную длину requestId (предотвращение DoS)
//        if (requestIdLength <= 0 || requestIdLength > 1024) { // Максимальная длина - 1KB
//            MyLogger.logServer("Invalid requestIdLength: " + requestIdLength, true);
//            throw new IllegalArgumentException("Invalid requestId length: must be between 1 and 1024 bytes");
//        }
//
//        // Проверяем, достаточно ли данных в буфере для извлечения requestId
//        if (payload.remaining() < requestIdLength) {
//            MyLogger.logServer("Invalid binary message: insufficient data for requestId content", true);
//            throw new IllegalArgumentException("Invalid binary message format: incomplete requestId");
//        }
//
//        byte[] requestIdBytes = new byte[requestIdLength];
//        payload.get(requestIdBytes);
//        String requestId = new String(requestIdBytes, StandardCharsets.UTF_8);
//        // Валидация requestId на инъекции и допустимые символы
//        if (!isValidRequestId(requestId)) {
//            MyLogger.logServer("Invalid requestId format: " + requestId, true);
//            throw new IllegalArgumentException("Invalid requestId format: contains disallowed characters");
//        }
//
//        return requestId;
//
//    }
//
//    private byte[] extractData(ByteBuffer payload) {
//        byte[] data = new byte[payload.remaining()];
//        payload.get(data);
//        return data;
//    }
//
//    @Override
//    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
//        //Синхронизация на уровне сессии (session) гарантирует, что все операции, связанные с одной сессией, выполняются последовательно.
//        synchronized (session) {
//            String deviceId = deviceSessionManager.getDeviceIdFromThisSession(session);
//            if (deviceId == null) {
//                MyLogger.logServer("Received message from unidentified session", true);
//                return;
//            }
//            MyLogger.logServer("Начинаем приём бинарных сообщений от устройства [" + deviceId + "]", true);
//
//            ByteBuffer payload = message.getPayload();
//            payload.rewind();
//
//            try {
//                // Извлекаем requestId и данные
//                String requestId = extractRequestId(payload);
//                byte[] data = extractData(payload);
//
//                MyLogger.logServer(requestId, true);
//
//                // Синхронизация на уровне requestId гарантирует, что данные для одного requestId обрабатываются последовательно.
//                synchronized (getBufferLock(requestId)) {
//                    // Получаем или создаем буфер
//                    ByteArrayOutputStream buffer = byteMessageBuffers.get(requestId);
//                    if (buffer == null) {
//                        buffer = new ByteArrayOutputStream();
//                        byteMessageBuffers.put(requestId, buffer);
//                    }
//
//                    // Записываем данные в буфер
//                    buffer.write(data);
//
//                    // Если это последний фрагмент, обрабатываем сообщение
//                    if (message.isLast()) {
//                        byte[] fullMessageBytes = buffer.toByteArray();
//                        byteMessageBuffers.remove(requestId);
//                        bufferLocks.remove(requestId); // Удаляем монитор
//                        MyLogger.logServer("Full binary response for requestId " + requestId + ", length: " + fullMessageBytes.length);
//                        handleBinaryResponse(requestId, fullMessageBytes);
//                    }
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//
//
//
//
//    private static String calculateHash(byte[] data) {
//        try {
//            MessageDigest digest = MessageDigest.getInstance("SHA-256");
//            byte[] hash = digest.digest(data);
//            return Base64.getEncoder().encodeToString(hash);
//        } catch (Exception e) {
//            return "Ошибка вычисления хэша";
//        }
//    }
//
//
//    // найти все ключи содержащие diviceID т.к. id запросов содержат id устройств
//    private List<String> completeTextResponseFutures(String deviceId) {
//        // Создаем список ключей, которые нужно удалить
//        List<String> keysToRemove = new ArrayList<>();
//        // Проходим по всем записям в responseFutures
//        for (String key : responseFutures.keySet()) {
//            if (key.contains(deviceId)) {
//                // Если ключ содержит deviceId, добавляем его в список для удаления
//                keysToRemove.add(key);
//            }
//        }
//        return keysToRemove;
//    }
//
//    // найти все ключи содержащие diviceID т.к. id запросов содержат id устройств
//    private List<String> completeBinaryResponseFutures(String deviceId) {
//        // Создаем список ключей, которые нужно удалить
//        List<String> keysToRemove = new ArrayList<>();
//        // Проходим по всем записям в responseFutures
//        for (String key : binaryResponseFutures.keySet()) {
//            if (key.contains(deviceId)) {
//                // Если ключ содержит deviceId, добавляем его в список для удаления
//                keysToRemove.add(key);
//            }
//        }
//        return keysToRemove;
//    }
//
//    @Override
//    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
//
//        String deviceId = deviceSessionManager.getDeviceIdFromThisSession(session);
//
//        //если это выход при существующем устройстве с таким же id то никаких объектов не создавалось в этой сессии поэтому тут делать нечего, просто выходим чтобы не ломать действующего подключения с таким же id устройства
//        if (status.getCode()==4000) {
//            MyLogger.logServer("deviseId ["+deviceId+"] неудачная попытка соединения с таким же id устройства");
//            return;
//        }
//
//
//        // Завершаем все CompletableFuture для этого устройства
//        CompletableFuture<String> textFuture;// = responseFutures.remove();
//        CompletableFuture<byte[]> binaryFuture;// = binaryResponseFutures.remove(deviceId);
//
//
//        //здесь добавить для текстового запроса или для бинарного как в ProxyController, а не оба сразу
//
//            if (deviceId != null) {
//                // Завершаем все CompletableFuture для найденных ключей
//                for (String key : completeTextResponseFutures(deviceId)) {
//                    textFuture = responseFutures.remove(key);
//                    if (textFuture != null && !textFuture.isDone()) {
//                        textFuture.completeExceptionally(new IOException("Connection closed"));
//                        MyLogger.logServer("Completed text future for device " + deviceId + " due to connection close",true);
//                        break;
//                    }
//                }
//
//                for (String key : completeBinaryResponseFutures(deviceId)) {
//                    binaryFuture = binaryResponseFutures.remove(key);
//                    if (binaryFuture != null && !binaryFuture.isDone()) {
//                        binaryFuture.completeExceptionally(new IOException("Connection closed"));
//                        MyLogger.logServer("Completed binary future for device " + deviceId + " due to connection close",true);
//                        break;
//                    }
//                }
//                }
//            // Удаляем сессию устройства
//            deviceSessionManager.removeDeviceWithSession(deviceId);
//            MyLogger.logServer("Device disconnected: " + deviceId);
//        }
//
//
//    public CompletableFuture<String> waitForResponse(String requestId) {
//        CompletableFuture<String> future = new CompletableFuture<>();
//        MyLogger.logServer("ждём текстовые данные для устройства requestId: " + requestId,true);
//        responseFutures.put(requestId, future);
//        return future;
//    }
//
//    public CompletableFuture<byte[]> waitForBinaryResponse(String requestId) {
//        CompletableFuture<byte[]> future = new CompletableFuture<>();
//        MyLogger.logServer("ждём бинарные данные для устройства requestId: " + requestId,true);
//        binaryResponseFutures.put(requestId, future);
//        return future;
//    }
//
//
//
//
//    // при получении полного текстового ответа выполнение  future.complete(response);
//    private void handleResponse(String requestId, String response) throws MyLogger.CustomException {
//        if (requestId == null) {
//            MyLogger.logServer("No requestId found in the response");
//            throw new MyLogger.CustomException("получили сообщение для неизвестного request id","request id null");
//        }
//
//        // Ищем CompletableFuture по requestId
//        CompletableFuture<String> future = responseFutures.remove(requestId);
//        if (future != null) {
//            MyLogger.logServer("Handling response for device requestId: " + requestId);
//    //        таким образом мы ассинхронно возвращаемся в точку вызова в класс ProxyController webSocketProxyHandler.waitForResponse(requestId);
//            future.complete(response);
//        } else {
//            MyLogger.logServer("Unexpected response from device, requestId: " + requestId);
//        }
//    }
//
//
//
//
////    // при получении полного бинарного ответа выполнение  future.complete(response);
////    private void handleBinaryResponse(String requestId, byte[] data, String contentType) {
////
////        if (requestId == null) {
////            MyLogger.logServer("No requestId found in the binary response");
////            return;
////        }
////
////        // Ищем CompletableFuture по requestId
////        CompletableFuture<byte[]> future = binaryResponseFutures.remove(requestId);
////        if (future != null) {
////            MyLogger.logServer("Handling binary response for device, requestId: " + requestId);
////
////            //правильные заголовки в этой data есть? сейчас
////            future.complete(data);
////        } else {
////            MyLogger.logServer("Unexpected binary response from device, requestId: " + requestId);
////        }
////    }
//
//    private void handleBinaryResponse(String requestId, byte[] data) {
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
//
//
////    private String getDeviceIdFromSession(WebSocketSession session) {
////        try {
////            String query = session.getUri().getQuery();
////            if (query != null && query.contains("deviceId=")) {
////                return query.split("deviceId=")[1];
////            }
////        } catch (Exception e) {
////            e.printStackTrace();
////        }
////        return null;
////    }
//
//}
//
//

