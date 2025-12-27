package com.websocketproxy.controller;

import com.websocketproxy.repository.database.DeviceToken;
import com.websocketproxy.services.DeviceTokenService;
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

//токен сохраняется в базу данных с привязкой к id пользователя. и передаётся пользователю в браузер.
//это нужно будет сделать только для зарегистрированных пользователей.
//а также записывать это в учётную запись пользователя. и при повторном вызове менять токен, но не менять deviceid

@RestController
@RequestMapping("/api/auth")
public class TokenController {

  private final AtomicLong deviceIdCounter = new AtomicLong(1); // Счетчик для генерации deviceId
  private DeviceTokenService deviceTokenService;

  @Autowired
  public TokenController(DeviceTokenService deviceTokenService) {
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

  // /**
  // * DELETE-запрос для отзыва токена (если нужно)
  // */
  // @DeleteMapping("/token")
  // public ResponseEntity<String> revokeToken(@RequestParam String deviceId) {
  // tokenAuthService.revokeToken(deviceId);
  // return ResponseEntity.ok("Token revoked successfully");
  // }
}

  
 
 
  
  
  
  
  
 
  
  
 
  
  
 
// 
  
 
  
  
  
  
 
  
  
  
  
  
  
  
  
  
  
  
// 
