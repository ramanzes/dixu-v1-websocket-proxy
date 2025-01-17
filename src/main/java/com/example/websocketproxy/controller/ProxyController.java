package com.example.websocketproxy.controller;

import com.example.websocketproxy.WebSocketProxyHandler;
import com.example.websocketproxy.service.DeviceSessionManager;
import com.example.websocketproxy.service.MyLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.BufferedReader;
import java.util.Enumeration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Controller
public class ProxyController {

    private final DeviceSessionManager deviceSessionManager;
    private final WebSocketProxyHandler webSocketProxyHandler;

    public ProxyController(DeviceSessionManager deviceSessionManager, WebSocketProxyHandler webSocketProxyHandler) {
        this.deviceSessionManager = deviceSessionManager;
        this.webSocketProxyHandler = webSocketProxyHandler;
    }

    @RequestMapping(value = "/p/{deviceId}/**", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> proxyRequest(@PathVariable String deviceId,
                                               HttpServletRequest request) {
        WebSocketSession deviceSession = deviceSessionManager.getSession(deviceId);

        if (deviceSession == null || !deviceSession.isOpen()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Device is not connected");
        }

        try {
            // Сборка HTTP-запроса
            StringBuilder requestBuilder = new StringBuilder();
            requestBuilder.append(request.getMethod()).append(" ").append(request.getRequestURI().replace("/p/" + deviceId, "")).append(" HTTP/1.1\n");
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = request.getHeader(headerName);
                requestBuilder.append(headerName).append(": ").append(headerValue).append("\n");
            }
            requestBuilder.append("\n");

            if ("POST".equalsIgnoreCase(request.getMethod())) {
                String body = new BufferedReader(request.getReader()).lines().collect(Collectors.joining("\n"));
                requestBuilder.append(body);
            }

            String httpRequest = requestBuilder.toString();

            // Отправляем запрос устройству через WebSocket
            deviceSession.sendMessage(new TextMessage(httpRequest+"это из приложения!!!"));
           // System.out.println("ждём ответ от устройства на запрос"+httpRequest+ "_____________________________________");
            // Ждем ответ от устройства
            CompletableFuture<String> responseFuture = webSocketProxyHandler.waitForResponse(deviceId);
            String deviceResponse = new String();
            try {
                MyLogger.processMessage("Waiting for response from device", deviceId);
                deviceResponse = responseFuture.get(60, TimeUnit.SECONDS);
                MyLogger.processMessage("Response received","");
            } catch (TimeoutException e) {
                MyLogger.processMessageErr("Timeout while waiting for response from device", deviceId, e);

            }
      //      String deviceResponse = responseFuture.get(30, TimeUnit.SECONDS); // Таймаут ожидания

            // Возвращаем ответ клиента
            System.out.println("возвращаем ответ от клиента");
            return ResponseEntity.ok(deviceResponse);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error occurred: " + e.getMessage());
        }
    }
}











//
//
//
//
//
//
//
//package com.example.websocketproxy.controller;
//
//import com.example.websocketproxy.WebSocketProxyHandler;
//import com.example.websocketproxy.service.DeviceSessionManager;
//import com.example.websocketproxy.websocket.ProxyWebSocketHandler;
//import jakarta.servlet.http.HttpServletRequest;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.*;
//import org.springframework.stereotype.Controller;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.client.RestTemplate;
//import org.springframework.web.socket.TextMessage;
//import org.springframework.web.socket.WebSocketSession;
//
//
//import org.springframework.http.ResponseEntity;
//import org.springframework.stereotype.Controller;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.Enumeration;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.TimeUnit;
//import java.util.stream.Collectors;
//
//
//@Controller
//public class ProxyController {
//
//    private final DeviceSessionManager deviceSessionManager;
//
//    public ProxyController(DeviceSessionManager deviceSessionManager) {
//        this.deviceSessionManager = deviceSessionManager;
//    }
//
//    @RequestMapping(value = "/proxy/{deviceId}/**", method = {RequestMethod.GET, RequestMethod.POST})
//    public ResponseEntity<String> proxyRequest(@PathVariable String deviceId,
//                                               HttpServletRequest request) {
//        // Находим WebSocket-соединение с устройством
//        WebSocketSession deviceSession = deviceSessionManager.getSession(deviceId);
//
//        if (deviceSession == null || !deviceSession.isOpen()) {
//            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Device is not connected");
//        }
//
//        try {
//            // Читаем HTTP-запрос от клиента
//            StringBuilder requestBuilder = new StringBuilder();
//            requestBuilder.append(request.getMethod()).append(" ").append(request.getRequestURI()).append(" HTTP/1.1\n");
//            Enumeration<String> headerNames = request.getHeaderNames();
//            while (headerNames.hasMoreElements()) {
//                String headerName = headerNames.nextElement();
//                String headerValue = request.getHeader(headerName);
//                requestBuilder.append(headerName).append(": ").append(headerValue).append("\n");
//            }
//            requestBuilder.append("\n");
//
//            // Если POST-запрос, добавляем тело
//            if ("POST".equalsIgnoreCase(request.getMethod())) {
//                String body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
//                requestBuilder.append(body);
//            }
//
//            // Отправляем HTTP-запрос устройству через WebSocket
//            String httpRequest = requestBuilder.toString();
//            deviceSession.sendMessage(new TextMessage(httpRequest));
//
//            // Ждем ответа от устройства
//            CompletableFuture<String> responseFuture = webSocketProxyHandler.waitForResponse(deviceId);
//            String deviceResponse = responseFuture.get(30, TimeUnit.SECONDS); // Таймаут ожидания ответа
//
//            // Возвращаем ответ клиента
//            return ResponseEntity.ok(deviceResponse);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error occurred: " + e.getMessage());
//        }
//    }
//}


//@Controller
//public class ProxyController {
//    // private final WebSocketProxyHandler webSocketProxyHandler;
//
//    //    public ProxyController(WebSocketProxyHandler webSocketProxyHandler) {
////        this.webSocketProxyHandler = webSocketProxyHandler;
////    }
//    private final DeviceSessionManager deviceSessionManager;
//    public ProxyController(DeviceSessionManager deviceSessionManager) {
//        this.deviceSessionManager = deviceSessionManager;
//    }
//
//    @Autowired
//    private RestTemplate restTemplate;
//    @RequestMapping(value = "/proxy/{deviceId}/**", method = {RequestMethod.GET, RequestMethod.POST})
//    public ResponseEntity<String> proxyRequest(@PathVariable String deviceId,
//                                               HttpServletRequest request) throws Exception {
//        String deviceIp = "localhost"; // Замените на IP устройства
//        //String targetUrl = "http://" + deviceIp+":8080";
//        String targetUrl = "http://" + deviceIp + ":80" + request.getRequestURI();
//
//        // Формирование запроса
//        HttpHeaders headers = new HttpHeaders();
//        request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
//            headers.add(headerName, request.getHeader(headerName));
//        });
//
//        HttpEntity<String> entity = new HttpEntity<>(null, headers);
//
//        // Отправка запроса устройству
//        ResponseEntity<String> response = restTemplate.exchange(targetUrl, HttpMethod.valueOf(request.getMethod()), entity, String.class);
//
//        // Возврат ответа клиенту
//        return ResponseEntity.status(response.getStatusCode())
//                .headers(response.getHeaders())
//                .body(response.getBody());
//    }
//}
//
//





//@Controller
//public class ProxyController {
//   // private final WebSocketProxyHandler webSocketProxyHandler;
//
////    public ProxyController(WebSocketProxyHandler webSocketProxyHandler) {
////        this.webSocketProxyHandler = webSocketProxyHandler;
////    }
//    private final DeviceSessionManager deviceSessionManager;
//    public ProxyController(DeviceSessionManager deviceSessionManager) {
//        this.deviceSessionManager = deviceSessionManager;
//    }
//    @RequestMapping(value = "/proxy/{deviceId}/**", method = {RequestMethod.GET, RequestMethod.POST})
//    public ResponseEntity<String> proxyRequest(@PathVariable String deviceId,
//                                               @RequestParam(required = false) String path,
//                                               @RequestBody(required = false) String body,
//                                               HttpMethod method) throws Exception {
////        WebSocketSession deviceSession = webSocketProxyHandler.getSession(deviceId);
//        WebSocketSession deviceSession = deviceSessionManager.getSession(deviceId);
//
//        //   System.out.println("ProxyController.proxyRequest "+deviceId+" "+deviceSession.getUri());
//        if (deviceSession == null || !deviceSession.isOpen()) {
//            return ResponseEntity.status(404).body("Device not connected");
//        }
//
//        // Отправляем запрос устройству через WebSocket
//        String request = method + " " + (path == null ? "/" : path) + "\n" + body;
//        deviceSession.sendMessage(new TextMessage(request));
//
////        // Получаем ответ от устройства
////        String response = webSocketProxyHandler.getResponseForRequest(deviceId);
////
////        return ResponseEntity.ok(response);
//
//
//        // Здесь можно добавить обработку ответа, если нужно
//        return ResponseEntity.ok("Request sent to device: " + deviceId);
//    }
//}


//
//@RestController
//public class ProxyController {
//    @Autowired
//    private ProxyWebSocketHandler proxyWebSocketHandler;
//
//    @GetMapping("/proxy/{deviceId}/**")
//    public ResponseEntity<String> proxyToDevice(@PathVariable String deviceId, HttpServletRequest request) {
//        WebSocketSession deviceSession = proxyWebSocketHandler.getDeviceSessions(deviceId);
//
//        if (deviceSession == null || !deviceSession.isOpen()) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Device not connected");
//        }
//
//        try {
//            String path = request.getRequestURI().split("/proxy/" + deviceId)[1];
//            String httpRequest = "GET " + path + " HTTP/1.1";
//
//            deviceSession.sendMessage(new TextMessage(httpRequest));
//
//            // Здесь ожидается ответ от устройства
//            // Для упрощения возвращаем заглушку
//            return ResponseEntity.ok("Proxied response from device");
//        } catch (Exception e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error proxying request");
//        }
//    }
//}



//@Controller
//@RequestMapping("/proxy")
//public class ProxyController {
//
//    private final ProxyWebSocketHandler webSocketHandler;
//
//    public ProxyController(ProxyWebSocketHandler webSocketHandler) {
//        this.webSocketHandler = webSocketHandler;
//    }
//
//    @GetMapping("/{deviceId}/**")
//    public ResponseEntity<String> proxyRequest(@PathVariable String deviceId,
//                                               @RequestParam String path,
//                                               @RequestBody(required = false) String body) {
//        try {
//            WebSocketSession session = webSocketHandler.getDeviceSession(deviceId);
//            if (session == null || !session.isOpen()) {
//                return ResponseEntity.status(404).body("Device not connected");
//            }
//
//            // Формируем HTTP-запрос в формате JSON
//            String request = String.format("{\"path\":\"%s\",\"body\":\"%s\"}", path, body);
//
//            // Отправляем запрос на устройство через WebSocket
//            session.sendMessage(new TextMessage(request));
//
//            // В ответе мы можем ожидать асинхронный ответ от устройства
//            // или возвращать сразу статус, что запрос принят
//            return ResponseEntity.ok("Request sent to device");
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body("Error: " + e.getMessage());
//        }
//    }
//}
