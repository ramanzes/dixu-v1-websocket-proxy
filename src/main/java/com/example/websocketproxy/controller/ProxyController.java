package com.example.websocketproxy.controller;

import com.example.websocketproxy.services.HttpRequest;
import com.example.websocketproxy.services.HttpResponse;
import com.example.websocketproxy.websocket.WebSocketProxyHandler;
import com.example.websocketproxy.services.DeviceSessionManager;
import com.example.websocketproxy.services.logsandexceptions.MyLogger;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;






@Controller
public class ProxyController {

    private final DeviceSessionManager deviceSessionManager;
    private final WebSocketProxyHandler webSocketProxyHandler;



//    public static byte[] decompressGzip(byte[] compressedData) {
//        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedData);
//             GZIPInputStream gzipStream = new GZIPInputStream(byteStream);
//             ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
//
//            byte[] buffer = new byte[1024];
//            int len;
//            while ((len = gzipStream.read(buffer)) != -1) {
//                outStream.write(buffer, 0, len);
//            }
//            return outStream.toByteArray();
//        } catch (Exception e) {
//            throw new RuntimeException("Ошибка при разжатии GZIP", e);
//        }
//    }


    public static Object decompressGzip(byte[] compressedData, HttpHeaders headers) {
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzipStream = new GZIPInputStream(byteStream);
             ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, len);
            }
            byte[] decompressedData = outStream.toByteArray();
            String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
            // Если это текст, преобразуем в строку
            if (contentType != null && HttpResponse.isTextResponse(contentType)) {
                return new String(decompressedData, StandardCharsets.UTF_8);
            }

            return (byte []) decompressedData; // Оставляем бинарные данные
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при разжатии GZIP", e);
        }
    }

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
            String requestId = HttpRequest.generateRequestId(deviceId);
            //строка запроса
            String requestPath = HttpRequest.getPathFromRequest(request,deviceId);


            //получаем тип контента из пути запроса/  !!! возможно здесь нужно как то надёжнее получать тип конетента для запроса
            String contentType = HttpRequest.getContentType(requestPath);

            //связываем тип контента с id этого запроса
//            RequestData.addContentTypeForRequestId(requestId,contentType);   //нигде не используется пока убрал

            // Сборка HTTP-запроса с добавлением requestId
            StringBuilder requestBuilder = new StringBuilder();
            requestBuilder.append(request.getMethod()).append(" ");

            // Добавляем путь и query parameters (если есть)
            String queryString = request.getQueryString(); // Получаем query parameters
            String fullPath = requestPath + (queryString != null ? "?" + queryString : "");
            requestBuilder.append(fullPath).append(" HTTP/1.1\n");

            // Добавляем requestId в заголовки
            requestBuilder.append("X-Request-Id: ").append(requestId).append("\n");

// здесь пока закоментил не вижу смысла дополнительно работать с куками если мы проксируем все поступающие заголовки из request

//            // В методе proxyRequest если уже у нас не первый запрос и в заголовках гоняем куки
//            String cookies = request.getHeader("Cookie");
//            if (cookies != null) {
//                requestBuilder.append("Cookie: ").append(cookies).append("\n");
////                MyLogger.logServer("Cookies: " + cookies,true); // Логируем cookies
//
//                MyLogger.logServer("Cookies: " + cookies); // Логируем cookies
//            }

            // Добавляем все остальные поля заголовка
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = request.getHeader(headerName);
                requestBuilder.append(headerName).append(": ").append(headerValue).append("\n");
                MyLogger.logServer(headerName, true);
            }
            requestBuilder.append("\n");

            if ("POST".equalsIgnoreCase(request.getMethod())) {
                String body = new BufferedReader(request.getReader()).lines().collect(Collectors.joining("\n"));
                requestBuilder.append(body);
            }

            String httpRequest = requestBuilder.toString();

            MyLogger.logServer("requestPath: ["+requestPath+"]");
            MyLogger.logServer("httpRequest: ["+httpRequest+"]");
            // Отправляем запрос устройству через WebSocket
            sendMessage(deviceSession, new TextMessage(httpRequest));

            // Ждем ответ от устройства
            //здесь можно изменить на ожидание приёма сразу обоих типов данных. т.к. бинарные данные имеют текстовый заголовок. но только чисто текстовые данные не имеют бинарных это нужно учесть.

//            if (HttpRequest.isHtmlPageRequest(requestPath))
                textResponseFuture = webSocketProxyHandler.waitForResponse(requestId);
//            else


            // текстовые данные  будут всегда т.к. мы получаем заголовки для обоих типов данных в тексте

            String textResponse = textResponseFuture.get(120, TimeUnit.SECONDS);

            //здесь нужно проверить что мы получили, если только заголовки, то будет бинарное тело.
            //если целиком сообщение то бинарных данных уже не будет

            //создаём хранилище для тела текстовых данных без заголовков
//            String response = "";

            HttpHeaders responseHeaders = HttpResponse.extractHeaders(textResponse);

            //меняем контет тип. дальше он работает для ответа
            contentType = responseHeaders.getFirst(HttpHeaders.CONTENT_TYPE);
            String responseTextBody = HttpResponse.extractBody(textResponse);



////          общее хранилище для полученных заголовков текущего ответа. либо текстовое, либо бинарное оба содержат текстовые заголовки
//            HttpHeaders responseHeaders = new HttpHeaders();
//            // Проверяем, содержит ли ответ только заголовки или заголовки с телом
//            if (textResponse.contains("\r\n\r\n")) {
//                // Разделяем заголовки и тело
//                String[] parts = textResponse.split("\r\n\r\n", 2);
//                String headers = parts[0];
//                response = parts.length > 1 ? parts[1] : "";
//
//                MyLogger.logServer("Заголовки:");
//                MyLogger.logServer(headers);
//                MyLogger.logServer("Тело:");
////                MyLogger.logServer(response);
//
//
//
//                // Парсим заголовки для любого типа ответа
//                for (String line : headers.split("\r\n")) {
//                    if (line.startsWith("Set-Cookie") || line.contains(":")) {
//                        String[] headerParts = line.split(":", 2);
//                        if (headerParts.length == 2) {
//                            String headerName = headerParts[0].trim();
//                            String headerValue = headerParts[1].trim();
//
//                            // Добавляем заголовки в ответ
//                            responseHeaders.add(headerName, headerValue);
//                        }
//                    }
//                }
//            }

            byte[] binaryResponse = null;
            if (responseTextBody.length()==0) {
                MyLogger.logServer("Ответ содержит только заголовки."+" значит ждём и бинарные данные");
                binaryResponseFuture = webSocketProxyHandler.waitForBinaryResponse(requestId);
                binaryResponse = binaryResponseFuture.get(120, TimeUnit.SECONDS);
            }







            // Ожидаем либо текстовый, либо бинарный ответ/ если это бинарные данные то в первой части у нас будет текстовый заголовок а в этой данные.
            // если же у нас только текстовые данные, то здесь у нас не будет ответа
       //     combinedFuture = CompletableFuture.anyOf(textResponseFuture, binaryResponseFuture);


//            вот здесь мы уже должны иметь ответ от клиента с заголовками в которых есть куки


//            //Ожидает завершения CompletableFuture с помощью combinedFuture.get(). т.е. когда будет получено всё сообщение отправленное по частям сможем продолжить
//            Object response = combinedFuture.get(120, TimeUnit.SECONDS); // Увеличиваем таймаут

//            if (HttpRequest.isHtmlPageRequest(requestPath)&&!(response instanceof String)){
////                MyLogger.logServer("ожидали получить текстовый ответ, а получили бинарный");
//                throw new MyLogger.CustomException("ожидали получить текстовый ответ, а получили бинарный","несоответствие ожиданий");
//            } else if (!HttpRequest.isHtmlPageRequest(requestPath)&&!(response instanceof byte[])){
////                MyLogger.logServer("ожидали получить бинарный ответ, а получили текстовый");
//                throw new MyLogger.CustomException("ожидали получить бинарный ответ, а получили текстовый","несоответствие ожиданий");
//            }

//            String headers="";

            if (binaryResponse==null ) {

                // Текстовый ответ
                MyLogger.logServer("возвращаем текстовый ответ от клиента");

//                String fullResponse = (String) response;

                // Разделяем заголовки и тело
//                String[] parts = fullResponse.split("\r\n\r\n", 2); // \r\n\r\n — разделитель между заголовками и телом

//                String body;
//
//                if (parts.length > 1) {
//                    headers = parts[0]; // Заголовки
//                    body = parts[1];           // Тело
//                    MyLogger.logServer("Заголовки:\n" + headers );
//                } else {
//                    body = fullResponse; // Если разделителя нет, вся строка считается телом
//                }

                // Создаем ResponseEntity с заголовками
//                HttpHeaders responseHeaders = new HttpHeaders();

//                // Парсим заголовки устройства
//                for (String line : headers.split("\r\n")) {
//                    if (line.startsWith("Set-Cookie") || line.contains(":")) {
//                        String[] headerParts = line.split(":", 2);
//                        if (headerParts.length == 2) {
//                            String headerName = headerParts[0].trim();
//                            String headerValue = headerParts[1].trim();
//
//                            // Добавляем заголовки в ответ
//                            responseHeaders.add(headerName, headerValue);
//                        }
//                    }
//                }

                // Возвращаем только тело ответа
//                return ResponseEntity.ok(body.getBytes(StandardCharsets.UTF_8));
                // Возвращаем текстовый ответ с заголовками
                return ResponseEntity.ok()
                        .headers(responseHeaders)
                        .body(responseTextBody.getBytes(StandardCharsets.UTF_8));

            }
            //  значит есть бинарные данные
            else {
                try {
                    MyLogger.logServer("Возвращаем бинарный ответ от клиента, contentType: " + contentType, true);

                    MediaType mediaType;
                    try {
                        mediaType = MediaType.valueOf(contentType);
                    } catch (InvalidMediaTypeException e) {
                        throw new IllegalArgumentException("Некорректный contentType: " + contentType, e);
                    }

//                    HttpHeaders responseHeaders = new HttpHeaders();

                    // Копируем заголовки устройства
//                    for (String line : headers.split("\r\n")) {
//                        if (line.startsWith("Set-Cookie") || line.contains(":")) {
//                            String[] headerParts = line.split(":", 2);
//                            if (headerParts.length == 2) {
//                                String headerName = headerParts[0].trim();
//                                String headerValue = headerParts[1].trim();
//
//                                // Добавляем заголовки в ответ
//                                responseHeaders.add(headerName, headerValue);
//                            }
//                        }
//                    }

                    Object body = null;
                    //  здесь логика можно распаковать как бинарное так и текстовое сообщение
                    if (HttpResponse.isGzipped(responseHeaders))  {
                        MyLogger.logServer("Данные сжаты, разжимаем...:\n");
                        body = decompressGzip(binaryResponse,responseHeaders);
                        responseHeaders.remove("Content-Encoding"); // Убираем, так как уже разархивировали
                        responseHeaders.remove("Transfer-Encoding");
                    }
                    if (body instanceof String){
                        //был сжат текстовый тип контента
                        return ResponseEntity.ok()
                                .headers(responseHeaders)
                                .body(responseTextBody.getBytes(StandardCharsets.UTF_8));
                    } else if (!body.equals(null)){
                        //были сжаты бинарные данные
                        return ResponseEntity.ok()
                                .headers(responseHeaders)
                                .contentType(mediaType)
                                .body(binaryResponse);
                    }
                    // данные не были сжаты это просто бинарные данные
                    return ResponseEntity.ok()
                            .headers(responseHeaders)
                            .contentType(mediaType)
                            .body(binaryResponse);

                } catch (Exception e) {
                    MyLogger.logServer("Ошибка при формировании ответа: " + e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error while processing binary response".getBytes(StandardCharsets.UTF_8));
                }
            }



//            else {
//                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected response type".getBytes(StandardCharsets.UTF_8));
//            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Error occurred: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }



}








