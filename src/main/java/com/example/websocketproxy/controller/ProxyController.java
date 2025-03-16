package com.example.websocketproxy.controller;

import com.example.websocketproxy.config.WebSocketConfig;
import com.example.websocketproxy.services.*;
import com.example.websocketproxy.services.HttpRequest;
import com.example.websocketproxy.websocket.WebSocketProxyHandler;
import com.example.websocketproxy.services.logsandexceptions.MyLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import java.util.Map;


@Controller
@RequestMapping(
        value = "/p"
)
public class ProxyController {

    private final DeviceSessionManager deviceSessionManager;
    private final WebSocketProxyHandler webSocketProxyHandler;
    private final MyWebsocketUtils myWebsocketUtils;
    private final HttpRequest httpRequest;
    private final HttpResponse httpResponse;
    private final HttpUtils httpUtils;
//    private WebSocketSession deviceSession;


    public ProxyController(DeviceSessionManager deviceSessionManager, WebSocketProxyHandler webSocketProxyHandler, MyWebsocketUtils myWebsocketUtils, HttpRequest httpRequest, HttpResponse httpResponse, HttpUtils httpUtils) {
        this.deviceSessionManager = deviceSessionManager;
        this.webSocketProxyHandler = webSocketProxyHandler;
        this.myWebsocketUtils = myWebsocketUtils;
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
        this.httpUtils = httpUtils;
    }



@RequestMapping(
        value = "/{deviceId}/**",
        method = {RequestMethod.GET}
)
public ResponseEntity<byte[]> proxyRequest(@PathVariable String deviceId,
                                           HttpServletRequest request) {
    WebSocketSession deviceSession = deviceSessionManager.getSession(deviceId);

    if (deviceSession == null || !deviceSession.isOpen()) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Device is not connected".getBytes(StandardCharsets.UTF_8));
    }

    try {
        // Формируем HTTP-запрос и получаем его вместе с requestId
        Map<String, String> httpRequestMap = httpUtils.buildHttpRequest(request, deviceId);
        // Извлекаем requestId и сам запрос из мапы
        String requestId = httpRequestMap.keySet().iterator().next();
        String httpRequest = httpRequestMap.get(requestId);

        MyLogger.logServer("requestPath: [ " + httpRequest.split("\n")[0] + "]");
        MyLogger.logServer("httpRequest: ["+httpRequest+"]");
        // Отправляем запрос устройству через WebSocket
        myWebsocketUtils.sendMessage(deviceSession, new TextMessage(httpRequest));


        // Вызываем метод для обработки ответа устройства
        return (ResponseEntity<byte[]>) httpResponse.processDeviceResponse(requestId,deviceId,webSocketProxyHandler,myWebsocketUtils);

    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Error occurred: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
    }
}

//обработка POST запросов для типа контента без multipart
@PostMapping(
        value = "/{deviceId}/**",
        consumes = {
            "application/json",
            "application/xml",
            "text/plain",
            "application/x-www-form-urlencoded",
            "application/octet-stream"
        }
)
public ResponseEntity<byte[]> proxyPostSimpleRequest(@PathVariable String deviceId,
                                           HttpServletRequest request) {
    WebSocketSession deviceSession = deviceSessionManager.getSession(deviceId);

    if (deviceSession == null || !deviceSession.isOpen()) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Device is not connected".getBytes(StandardCharsets.UTF_8));
    }

    try {
        // Формируем HTTP-запрос и получаем его вместе с requestId
        Map<String, String> httpRequestMap = httpUtils.buildHttpRequest(request, deviceId);
        // Извлекаем requestId и сам запрос из мапы
        String requestId = httpRequestMap.keySet().iterator().next();
        String httpRequest = httpRequestMap.get(requestId);

        MyLogger.logServer("requestPath: [ " + httpRequest.split("\n")[0] + "]");
        MyLogger.logServer("httpRequest: ["+httpRequest+"]");
        // Отправляем запрос устройству через WebSocket / этот запрос создаст хранилище в onMessage для получения тела post по этому idRequest
        myWebsocketUtils.sendMessage(deviceSession, new TextMessage(httpRequest));
        //инициируем переменную для приёма потока данных от клиента из его запроса, из тела post
        InputStream inputStream = request.getInputStream();
        int initialBufferSize = WebSocketConfig.BUFFER_SIZE;

        // Читаем первые байты, чтобы определить стратегию
        byte[] initialBuffer = new byte[initialBufferSize];
        // и сохраняем её в initialBuffer
        int bytesRead = inputStream.read(initialBuffer);

        if (bytesRead == -1) {
            MyLogger.logServer("POST-запрос пустой", true);
            throw new MyLogger.CustomException("пустое тело запроса post");
//                    return;
        }
//        boolean shouldCompress = myWebsocketUtils.shouldCompress(request.getContentType(), bytesRead, request.getRequestURI());

        byte[] requestBody;

        if(bytesRead < 512){
            MyLogger.logServer("Тело POST имеет маленький размер, отправляем сразу без сжатия в одном бинарном запросе, размер" + bytesRead + " байт");
            // Отправляем только прочитанные данные
            byte[] dataToSend = Arrays.copyOf(initialBuffer, bytesRead);
            myWebsocketUtils.sendBinaryMessage(deviceSession, requestId, dataToSend, true, false);
        //иначе если данные большие и должны быть сжаты
//!!!!!! здесь нужно проверять если данные уже сжаты в заголовках взять инфу, то соответственно сжимать их уже не нужно это раз. но флаг gzip должен быть true/ А может ли клиент браузера вообще сам сжимать данные? при отправке данных пост
        } else if (myWebsocketUtils.shouldCompress(request.getContentType(), bytesRead, request.getRequestURI()))  {
            //по идее сюда мы попадаем только в том случае если пользовательский браузер не поддерживает сжатие
            // или клиентски локальный сервер не поддерживает сжатие
            // тогда в заголовках это указано и пользователь отправляет на прокси не сжатые большие данные, мы их сжимаем за него и проксируем на клиента - локальный севрер, и расжимаем по своей логике на клиенте и передаём на его локальный сервер расжатые данные


            //!!! но если данные были сжаты браузером то наша логика на клиенте должна и это учитывать! т.е. мы должны проксировать сжатые данные как сжатые данные по стандарной логике... на клиентский локальный сервер

            requestBody = MyWebsocketUtils.compressData(initialBuffer);
            int compressDataLength = requestBody.length;
            MyLogger.logServer("Тело POST, размер до сжатия: " + bytesRead + " байт");
            MyLogger.logServer("Тело POST сжато (GZIP), размер после: " + compressDataLength + " байт");
//            MyLogger.logServByteToString(initialBuffer);
            // значит отправлено не всё и скорее всего остались в потоке ещё данные
            if (bytesRead == WebSocketConfig.BUFFER_SIZE) {
                //отправляем первую часть уже прочитанных данных
                myWebsocketUtils.sendBinaryMessage(deviceSession, requestId, requestBody, false, true);
                //отправляем остальные данные частями, начиная от конца уже отправленной первой части.
//                myWebsocketUtils.sendChunkInputStream(deviceSession, requestId, inputStream, true, compressDataLength);
//                это делает движок сам. т.е. читает данные следующие от прочитанных из потока
                myWebsocketUtils.sendChunkInputStream(deviceSession, requestId, inputStream, true);
            } else if (bytesRead < WebSocketConfig.BUFFER_SIZE){
                myWebsocketUtils.sendBinaryMessage(deviceSession, requestId, requestBody, true, true);
            }
        } else{
            //это могут быть большие данные уже сжатые или не требующие сжатия по тем или иным причинам
            MyLogger.logServer("Тело POST отправляется потоком без сжатия");

            if (bytesRead < WebSocketConfig.BUFFER_SIZE) {
                //отправляем уже считанную часть в самом начале
                myWebsocketUtils.sendBinaryMessage(deviceSession, requestId, initialBuffer, true, false);
            } else if (bytesRead == WebSocketConfig.BUFFER_SIZE) {
                //отправляем уже считанную часть в самом начале
                myWebsocketUtils.sendBinaryMessage(deviceSession, requestId, initialBuffer, false, false);
                // отправляем остальные части т.е. из inputStream уже считана первая часть продолжим от туда
//                myWebsocketUtils.sendChunkInputStream(deviceSession, requestId, inputStream, false, bytesRead);
// движок сам должен давать только следующие данные из потока
                myWebsocketUtils.sendChunkInputStream(deviceSession, requestId, inputStream, false);
                // Вызываем новый метод для обработки ответа устройства
            }
        }

        inputStream.close();

        // Вызываем новый метод для обработки ответа устройства
        return (ResponseEntity<byte[]>) httpResponse.processDeviceResponse(requestId,deviceId,webSocketProxyHandler,myWebsocketUtils);

    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Error occurred: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
    }
}


//контроллер обрабатывает Post запросы multipart

    @PostMapping(
            value = "/{deviceId}/**",
            consumes = {
                    "multipart/form-data"
            }
    )
    public ResponseEntity<byte[]> proxyMultipartPostRequest(@PathVariable String deviceId,
                                                            MultipartHttpServletRequest request) {
        WebSocketSession deviceSession = deviceSessionManager.getSession(deviceId);
        if (deviceSession == null || !deviceSession.isOpen()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Device is not connected".getBytes(StandardCharsets.UTF_8));
        }

        try {
            // Формируем HTTP-запрос и получаем его вместе с requestId
            Map<String, String> httpRequestMap = httpUtils.buildHttpRequest(request, deviceId);
            // Извлекаем requestId и сам запрос из мапы
            String requestId = httpRequestMap.keySet().iterator().next();
            String httpRequest = httpRequestMap.get(requestId);

            MyLogger.logServer("requestPath: [ " + httpRequest.split("\n")[0] + "]");
            MyLogger.logServer("httpRequest: [" + httpRequest + "]");

            // Отправляем запрос устройству через WebSocket
            myWebsocketUtils.sendMessage(deviceSession, new TextMessage(httpRequest));

            // Получаем boundary из заголовка Content-Type
            String contentType = request.getContentType();
            String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);

            // Обрабатываем multipart-данные
            for (Map.Entry<String, MultipartFile> entry : request.getFileMap().entrySet()) {
                MultipartFile file = entry.getValue();
                String fileName = file.getOriginalFilename();
                MyLogger.logServer("Обрабатываем файл: " + fileName);

                // Отправляем файл потоком
               myWebsocketUtils.sendMultipartFormDataStream(deviceSession, requestId, boundary, entry.getKey(), file);
            }

            // Обрабатываем текстовые данные формы (если есть)
            Map<String, String[]> formData = request.getParameterMap();
            for (Map.Entry<String, String[]> entry : formData.entrySet()) {
                String key = entry.getKey();
                String[] values = entry.getValue();
                MyLogger.logServer("Текстовые данные формы: " + key + " = " + String.join(", ", values));
            }

            // Вызываем метод для обработки ответа устройства
            return (ResponseEntity<byte[]>) httpResponse.processDeviceResponse(requestId, deviceId, webSocketProxyHandler, myWebsocketUtils);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error occurred: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }



//    @PostMapping(
//            value = "p/{deviceId}/**",
//            consumes = {
//                    "multipart/form-data"
//            }
//    )
//    public ResponseEntity<byte[]> proxyMultipartPostRequest(@PathVariable String deviceId,
//                                                            MultipartHttpServletRequest request) {
//        WebSocketSession deviceSession = deviceSessionManager.getSession(deviceId);
//        if (deviceSession == null || !deviceSession.isOpen()) {
//            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Device is not connected".getBytes(StandardCharsets.UTF_8));
//        }
//
//        try {
//            // Формируем HTTP-запрос и получаем его вместе с requestId
//            Map<String, String> httpRequestMap = httpRequest.buildHttpRequest(request, deviceId);
//            // Извлекаем requestId и сам запрос из мапы
//            String requestId = httpRequestMap.keySet().iterator().next();
//            String httpRequest = httpRequestMap.get(requestId);
//
//            MyLogger.logServer("requestPath: [ " + httpRequest.split("\n")[0] + "]");
//            MyLogger.logServer("httpRequest: [" + httpRequest + "]");
//
//            // Отправляем запрос устройству через WebSocket
//            myWebsocketUtils.sendMessage(deviceSession, new TextMessage(httpRequest));
//
//            // Получаем boundary из заголовка Content-Type
//            String contentType = request.getContentType();
//            String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
//
//            // Обрабатываем multipart-данные
//            for (Map.Entry<String, MultipartFile> entry : request.getFileMap().entrySet()) {
//                MultipartFile file = entry.getValue();
//                String fileName = file.getOriginalFilename();
//                MyLogger.logServer("Обрабатываем файл: " + fileName);
//
//                // Формируем multipart/form-data
//                byte[] multipartData = HttpRequest.buildMultipartFormData(boundary, entry.getKey(), file);
//
//                // Отправляем данные через WebSocket
//                myWebsocketUtils.sendBinaryMessage(deviceSession, requestId, multipartData, true, false);
//            }
//
//            // Обрабатываем текстовые данные формы (если есть)
//            Map<String, String[]> formData = request.getParameterMap();
//            for (Map.Entry<String, String[]> entry : formData.entrySet()) {
//                String key = entry.getKey();
//                String[] values = entry.getValue();
//                MyLogger.logServer("Текстовые данные формы: " + key + " = " + String.join(", ", values));
//            }
//
//            // Вызываем метод для обработки ответа устройства
//            return (ResponseEntity<byte[]>) httpResponse.processDeviceResponse(requestId, deviceId, webSocketProxyHandler, myWebsocketUtils);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(("Error occurred: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
//        }
//    }



//@PostMapping(
//        value = "p/{deviceId}/**",
//        consumes = {
//                "multipart/form-data"
//        }
//)
//public ResponseEntity<byte[]> proxyMultipartPostRequest(@PathVariable String deviceId,
//                                                        MultipartHttpServletRequest request) {
//    WebSocketSession deviceSession = deviceSessionManager.getSession(deviceId);
//    if (deviceSession == null || !deviceSession.isOpen()) {
//        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Device is not connected".getBytes(StandardCharsets.UTF_8));
//    }
//
//    try {
//        // Формируем HTTP-запрос и получаем его вместе с requestId
//        Map<String, String> httpRequestMap = httpRequest.buildHttpRequest(request, deviceId);
//        // Извлекаем requestId и сам запрос из мапы
//        String requestId = httpRequestMap.keySet().iterator().next();
//        String httpRequest = httpRequestMap.get(requestId);
//
//        MyLogger.logServer("requestPath: [ " + httpRequest.split("\n")[0] + "]");
//        MyLogger.logServer("httpRequest: [" + httpRequest + "]");
//
//        // Отправляем запрос устройству через WebSocket
//        myWebsocketUtils.sendMessage(deviceSession, new TextMessage(httpRequest));
//
//        // Обрабатываем multipart-данные
//        for (Map.Entry<String, MultipartFile> entry : request.getFileMap().entrySet()) {
//            MultipartFile file = entry.getValue();
//            String fileName = file.getOriginalFilename();
//            InputStream inputStream = file.getInputStream();
//
//            MyLogger.logServer("Обрабатываем файл: " + fileName);
//
//            // Читаем первые байты файла
//            byte[] initialBuffer = new byte[WebSocketConfig.BUFFER_SIZE];
//            int bytesRead = inputStream.read(initialBuffer);
//
//            if (bytesRead == -1) {
//                MyLogger.logServer("Файл пустой: " + fileName, true);
//                continue;
//            }
//
//            boolean shouldCompress = myWebsocketUtils.shouldCompress(file.getContentType(), bytesRead, request.getRequestURI());
//
//            if (shouldCompress) {
//                byte[] compressedData = MyWebsocketUtils.compressData(initialBuffer);
//                MyLogger.logServer("Файл сжат (GZIP), размер: " + compressedData.length + " байт");
//                myWebsocketUtils.sendBinaryMessage(deviceSession, requestId, compressedData, true, true);
//
//                // Если файл больше буфера, отправляем оставшиеся части
//                if (bytesRead == WebSocketConfig.BUFFER_SIZE) {
//                    myWebsocketUtils.sendChunkInputStream(deviceSession, requestId, inputStream, true);
//                }
//            } else {
//                MyLogger.logServer("Файл отправляется потоком без сжатия");
//                myWebsocketUtils.sendBinaryMessage(deviceSession, requestId, initialBuffer, false, false);
//                myWebsocketUtils.sendChunkInputStream(deviceSession, requestId, inputStream, false);
//            }
//
//            inputStream.close();
//        }
//
//        // Обрабатываем текстовые данные формы (если есть)
//        Map<String, String[]> formData = request.getParameterMap();
//        for (Map.Entry<String, String[]> entry : formData.entrySet()) {
//            String key = entry.getKey();
//            String[] values = entry.getValue();
//            MyLogger.logServer("Текстовые данные формы: " + key + " = " + String.join(", ", values));
//        }
//
//        // Вызываем метод для обработки ответа устройства
//        return (ResponseEntity<byte[]>) httpResponse.processDeviceResponse(requestId, deviceId, webSocketProxyHandler, myWebsocketUtils);
//
//    } catch (Exception e) {
//        e.printStackTrace();
//        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body(("Error occurred: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
//    }
//}

}








