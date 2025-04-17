package com.websocketproxy.controller;

import com.websocketproxy.repository.database.DeviceToken;
import com.websocketproxy.services.DeviceTokenService;
import com.websocketproxy.services.TokenAuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

//https://localhost:8443/api/auth/token получаем токен в браузере
//в будущем это будет привязано к авторизации пользователя на сервисе
//ему выдаётся id устройства и его токен.
// которые будут завязаны в базе данных как ключ - значение
//возможно это будет приватный метод внутренний который будет вызываться из
//авторизации

@RestController
@RequestMapping("/api/auth")
public class TokenController {

    private final TokenAuthenticationService tokenAuthService;
    private final AtomicLong deviceIdCounter = new AtomicLong(1); // Счетчик для генерации deviceId
    private DeviceTokenService deviceTokenService;

    @Autowired
    public TokenController(TokenAuthenticationService tokenAuthService, DeviceTokenService deviceTokenService) {
        this.tokenAuthService = tokenAuthService;
        this.deviceTokenService = deviceTokenService;
    }

    /**
     * GET-запрос для получения токена и deviceId
     */
    @GetMapping("/token")
    public ResponseEntity<Map<String, String>> getToken() {
        DeviceToken deviceToken = deviceTokenService.createNewDeviceToken();
        // Возвращаем ответ
        Map<String, String> response = new HashMap<>();
        response.put("deviceId", deviceToken.getDeviceId());
        response.put("token", deviceToken.getToken());
        return ResponseEntity.ok(response);
    }

//    /**
//     * DELETE-запрос для отзыва токена (если нужно)
//     */
//    @DeleteMapping("/token")
//    public ResponseEntity<String> revokeToken(@RequestParam String deviceId) {
//        tokenAuthService.revokeToken(deviceId);
//        return ResponseEntity.ok("Token revoked successfully");
//    }
}



//package com.websocketproxy.controller;
//
//
//import com.websocketproxy.services.TokenAuthenticationService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.HashMap;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/auth")
//public class TokenController {
//
//    private final TokenAuthenticationService tokenAuthService;
//
//    @Autowired
//    public TokenController(TokenAuthenticationService tokenAuthService) {
//        this.tokenAuthService = tokenAuthService;
//    }
//
//    /**
//     * Endpoint to request a new token
//     * In production, this should include proper authentication
//     */
//    @PostMapping("/token")
//    public ResponseEntity<?> getToken(@RequestParam String deviceId,
//                                      @RequestHeader("X-API-Key") String apiKey) {
//        // Verify API key or other credentials before issuing token
//        // This is a simplified example - in production use proper authentication
//        if (!isValidApiKey(apiKey)) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid API key");
//        }
//
//        // Generate token
//        String token = tokenAuthService.generateAndStoreToken(deviceId);
//
//        // Return token
//        Map<String, String> response = new HashMap<>();
//        response.put("deviceId", deviceId);
//        response.put("token", token);
//
//        return ResponseEntity.ok(response);
//    }
//
//    /**
//     * Endpoint to revoke a token
//     */
//    @DeleteMapping("/token")
//    public ResponseEntity<?> revokeToken(@RequestParam String deviceId,
//                                         @RequestHeader("X-API-Key") String apiKey) {
//        // Verify API key
//        if (!isValidApiKey(apiKey)) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid API key");
//        }
//
//        // Revoke token
//        tokenAuthService.revokeToken(deviceId);
//
//        return ResponseEntity.ok("Token revoked successfully");
//    }
//
//    /**
//     * Verify if API key is valid
//     */
//    private boolean isValidApiKey(String apiKey) {
//        // In production, validate against stored API keys
//        // This is a simplified example
//        return "your-secure-api-key".equals(apiKey);
//    }
//}