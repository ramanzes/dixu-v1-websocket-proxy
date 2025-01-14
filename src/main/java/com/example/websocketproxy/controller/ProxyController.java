package com.example.websocketproxy.controller;

import com.example.websocketproxy.WebSocketProxyHandler;
import com.example.websocketproxy.websocket.ProxyWebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;





import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class ProxyController {
    private final WebSocketProxyHandler webSocketProxyHandler;

    public ProxyController(WebSocketProxyHandler webSocketProxyHandler) {
        this.webSocketProxyHandler = webSocketProxyHandler;
    }

    @RequestMapping(value = "/proxy/{deviceId}/**", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> proxyRequest(@PathVariable String deviceId,
                                               @RequestParam(required = false) String path,
                                               @RequestBody(required = false) String body,
                                               HttpMethod method) throws Exception {
        WebSocketSession deviceSession = webSocketProxyHandler.getSession(deviceId);

        if (deviceSession == null || !deviceSession.isOpen()) {
            return ResponseEntity.status(404).body("Device not connected");
        }

        // Отправляем запрос устройству через WebSocket
        String request = method + " " + (path == null ? "/" : path) + "\n" + body;
        deviceSession.sendMessage(new TextMessage(request));

        // Получаем ответ от устройства
        String response = webSocketProxyHandler.getResponseForRequest(deviceId);

        return ResponseEntity.ok(response);
    }
}


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
