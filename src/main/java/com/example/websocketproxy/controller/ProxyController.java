package com.example.websocketproxy.controller;

import com.example.websocketproxy.services.HttpRequest;
import com.example.websocketproxy.services.HttpResponse;
import com.example.websocketproxy.services.MyHttpUtils;
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

import static com.example.websocketproxy.services.MyHttpUtils.decompress;


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
//        CompletableFuture<Object> combinedFuture = new CompletableFuture<>();


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

            // Сборка HTTP-запроса с добавлением requestId
            StringBuilder requestBuilder = new StringBuilder();
            requestBuilder.append(request.getMethod()).append(" ");

            // Добавляем путь и query parameters (если есть)
            String queryString = request.getQueryString(); // Получаем query parameters
            String fullPath = requestPath + (queryString != null ? "?" + queryString : "");
            requestBuilder.append(fullPath).append(" HTTP/1.1\n");

            // Добавляем requestId в заголовки
            requestBuilder.append("X-Request-Id: ").append(requestId).append("\n");


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

            HttpHeaders responseHeaders = HttpResponse.extractHeaders(textResponse);

            //меняем контет тип. дальше он работает для ответа
            contentType = responseHeaders.getFirst(HttpHeaders.CONTENT_TYPE);
            String responseTextBody = HttpResponse.extractBody(textResponse);
            int statusCode = HttpResponse.extractStatusCode(textResponse);


            byte[] binaryResponse = null;
            if (responseTextBody.length()==0 && statusCode!=304 && statusCode!=204 && statusCode!=205) {
                MyLogger.logServer("Ответ содержит только заголовки."+" значит ждём и бинарные данные");
                // получаем бинарные данные для того же запроса т.к. requestId прежний, как у полученного заголовка
                binaryResponseFuture = webSocketProxyHandler.waitForBinaryResponse(requestId);
                binaryResponse = binaryResponseFuture.get(120, TimeUnit.SECONDS);
            }







            // Ожидаем либо текстовый, либо бинарный ответ/ если это бинарные данные то в первой части у нас будет текстовый заголовок а в этой данные.
            // если же у нас только текстовые данные, то здесь у нас не будет ответа
       //     combinedFuture = CompletableFuture.anyOf(textResponseFuture, binaryResponseFuture);




            if (binaryResponse==null ) {

                // Текстовый ответ
                MyLogger.logServer("возвращаем текстовый ответ от клиента");

                // Возвращаем только тело ответа
//                return ResponseEntity.ok(body.getBytes(StandardCharsets.UTF_8));

                String updateBody = HttpResponse.modifyHtmlPaths(String.valueOf(responseTextBody),contentType,deviceId);
                // Возвращаем текстовый ответ с заголовками
                return ResponseEntity.ok()
                        .headers(responseHeaders)
                        .body(updateBody.getBytes(StandardCharsets.UTF_8));
//                        .body(responseTextBody.getBytes(StandardCharsets.UTF_8));

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


                    Object body = null;
                    //  здесь логика можно распаковать как бинарное так и текстовое сообщение
                    // также проверить метод isGzipped все ли типы сжатых данных он обработает или только gzip сейчас
                    if (MyHttpUtils.isCompressed(responseHeaders))  {
                        MyLogger.logServer("Данные сжаты, разжимаем...:\n");
                        body = decompress(binaryResponse,responseHeaders);
                        responseHeaders.remove("Content-Encoding"); // Убираем, так как уже разархивировали
                        responseHeaders.remove("Transfer-Encoding");
                    } else {
                        body = binaryResponse;
                    }
                    if (body instanceof String){
                        String updateBody = HttpResponse.modifyHtmlPaths(String.valueOf(body),contentType,deviceId);
                        //был сжат текстовый тип контента
                        byte[] textBytes = ((String) updateBody).getBytes(StandardCharsets.UTF_8);
                        responseHeaders.setContentLength(textBytes.length); // Устанавливаем реальную длину
                        return ResponseEntity.ok()
                                .headers(responseHeaders)
                                .body(textBytes);
                    } else {
                        return ResponseEntity.ok()
                                .headers(responseHeaders)
                                .contentType(mediaType)
                                .body((byte[]) body);
                    }

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








