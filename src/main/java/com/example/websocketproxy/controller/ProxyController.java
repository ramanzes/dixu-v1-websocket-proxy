package com.example.websocketproxy.controller;

import com.example.websocketproxy.service.RequestData;
import com.example.websocketproxy.websocket.WebSocketProxyHandler;
import com.example.websocketproxy.service.DeviceSessionManager;
import com.example.websocketproxy.service.MyLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Controller
public class ProxyController {

    private final DeviceSessionManager deviceSessionManager;
    private final WebSocketProxyHandler webSocketProxyHandler;

    public ProxyController(DeviceSessionManager deviceSessionManager, WebSocketProxyHandler webSocketProxyHandler) {
        this.deviceSessionManager = deviceSessionManager;
        this.webSocketProxyHandler = webSocketProxyHandler;
    }

    private final Object sendLock = new Object(); // Объект для синхронизации
//можно попробовать и без синхронизации, это было сделоно то того как у каждого запроса был уникальный идентификатор
    public void sendMessage(WebSocketSession session, TextMessage message) throws IOException, IOException {
        synchronized (sendLock) {
            session.sendMessage(message);
        }
    }




    @RequestMapping(value = "/p/{deviceId}/**", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<byte[]> proxyRequest(@PathVariable String deviceId,
                                               HttpServletRequest request) {
        WebSocketSession deviceSession = deviceSessionManager.getSession(deviceId);

        CompletableFuture<String> textResponseFuture = new CompletableFuture<>();
        CompletableFuture<byte[]> binaryResponseFuture = new CompletableFuture<>();
        CompletableFuture<Object> combinedFuture = new CompletableFuture<>();


        if (deviceSession == null || !deviceSession.isOpen()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Device is not connected".getBytes(StandardCharsets.UTF_8));
        }

        try {
            // Генерация уникального requestId для каждого запроса пользователя
            String requestId = RequestData.generateRequestId(deviceId);
            //строка запроса
            String requestPath = RequestData.getPathFromRequest(request,deviceId);
            //получаем тип контента из пути запроса
            String contentType = RequestData.getContentType(requestPath);
            //связываем тип контента с id этого запроса
            RequestData.addContentTypeForRequestId(requestId,contentType);

            // Сборка HTTP-запроса с добавлением requestId
            StringBuilder requestBuilder = new StringBuilder();
            requestBuilder.append(request.getMethod()).append(" ").append(request.getRequestURI().replace("/p/" + deviceId, "")).append(" HTTP/1.1\n");
            requestBuilder.append("X-Request-Id: ").append(requestId).append("\n"); // Добавляем requestId в заголовки
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = request.getHeader(headerName);
                requestBuilder.append(headerName).append(": ").append(headerValue).append("\n");
                MyLogger.logServer(headerName,true);
            }
            requestBuilder.append("\n");

            if ("POST".equalsIgnoreCase(request.getMethod())) {
                String body = new BufferedReader(request.getReader()).lines().collect(Collectors.joining("\n"));
                requestBuilder.append(body);
            }

            String httpRequest = requestBuilder.toString();

            // Отправляем запрос устройству через WebSocket
            sendMessage(deviceSession, new TextMessage(httpRequest));

            // Ждем ответ от устройства
            // здесь уже нужно понимать какой у нас запрос на текстовые или бинарные данные
            //чтобы не ждать того чего нет по этому запросу. а так мы ждём и то и то сейчас.

            if (RequestData.isHtmlPageRequest(requestPath))
                textResponseFuture = webSocketProxyHandler.waitForResponse(requestId);
            else
                binaryResponseFuture = webSocketProxyHandler.waitForBinaryResponse(requestId);

            // Ожидаем либо текстовый, либо бинарный ответ
            combinedFuture = CompletableFuture.anyOf(textResponseFuture, binaryResponseFuture);

            //Ожидает завершения CompletableFuture с помощью combinedFuture.get(). т.е. когда будет получено всё сообщение отправленное по частям сможем продолжить
            Object response = combinedFuture.get(120, TimeUnit.SECONDS); // Увеличиваем таймаут

            if (RequestData.isHtmlPageRequest(requestPath)&&!(response instanceof String)){
//                MyLogger.logServer("ожидали получить текстовый ответ, а получили бинарный");
                throw new MyLogger.CustomException("ожидали получить текстовый ответ, а получили бинарный","несоответствие ожиданий");
            } else if (!RequestData.isHtmlPageRequest(requestPath)&&!(response instanceof byte[])){
//                MyLogger.logServer("ожидали получить бинарный ответ, а получили текстовый");
                throw new MyLogger.CustomException("ожидали получить бинарный ответ, а получили текстовый","несоответствие ожиданий");
            }

            if (response instanceof String) {
                // Текстовый ответ
                MyLogger.logServer("возвращаем текстовый ответ от клиента",true);

                String fullResponse = (String) response;

                // Разделяем заголовки и тело
                String[] parts = fullResponse.split("\r\n\r\n", 2); // \r\n\r\n — разделитель между заголовками и телом

                String body;
                if (parts.length == 2) {
                    String headers = parts[0]; // Заголовки
                    body = parts[1];           // Тело
                    MyLogger.logServer("Заголовки:\n" + headers, true);
                } else {
                    body = fullResponse; // Если разделителя нет, вся строка считается телом
                }

                // Возвращаем только тело ответа
                return ResponseEntity.ok(body.getBytes(StandardCharsets.UTF_8));

            } else if (response instanceof byte[]) {
                try {
                    MyLogger.logServer("Возвращаем бинарный ответ от клиента, contentType: " + contentType,true);

                    MediaType mediaType;
                    try {
                        mediaType = MediaType.valueOf(contentType);
                    } catch (InvalidMediaTypeException e) {
//                        MyLogger.logServer("Ошибка в contentType: " + contentType);
                        throw new IllegalArgumentException("Некорректный contentType: " + contentType, e);
                    }

                    return ResponseEntity.ok()
                            .contentType(mediaType)
                            .body((byte[]) response);
                } catch (Exception e) {
                    MyLogger.logServer("Ошибка при формировании ответа: " + e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error while processing binary response".getBytes(StandardCharsets.UTF_8));
                }
            }



            else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected response type".getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Error occurred: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }



}








