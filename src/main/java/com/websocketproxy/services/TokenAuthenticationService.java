package com.websocketproxy.services;

import com.websocketproxy.services.logsandexceptions.MyLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.annotation.PostConstruct;

@Service
public class TokenAuthenticationService {
   DeviceTokenService deviceTokenService;

    public TokenAuthenticationService(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    // Secret key used for token generation (should be in application properties)
    @Value("${websocket.security.secret:your-default-secret-key}")
    private String secretKey;

    // Store of valid tokens (can be replaced with a database or Redis)
    private final Map<String, String> deviceTokenMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // For testing, add some default tokens
        // In production, load from database or generate during device registration
//        MyLogger.logServer("TOKEN = "+generateAndStoreToken("device-1234"));

        // ... add more devices as needed
    }

    /**
     * Validates a token for a specific deviceId
     *
     * @param deviceId The device identifier
     * @param token The token to validate
     * @return true if token is valid, false otherwise
     */
    public boolean validateToken(String deviceId, String token) {

        return deviceTokenService.getTokenByDeviceId(deviceId).equals(token);

    }

//    /**
//     * Generates and stores a new token for a device
//     *
//     * @param deviceId The device identifier
//     * @return The generated token
//     */
//    public static String generateAndStoreToken(String deviceId) {
//        // Generate a unique token
//        String token = generateToken(deviceId);
//        return token;
//    }
//
//    /**
//     * Revokes a token for a device
//     *
//     * @param deviceId The device identifier
//     */
//    public void revokeToken(String deviceId) {
////        deviceTokenMap.remove(deviceId);
//    }
//
//    /**
//     * Generates a token for a device using HMAC-SHA256
//     *
//     * @param deviceId The device identifier
//     * @return Generated token
//     */
//    private static String generateToken(String deviceId) {
//        try {
//            String secretKey = "goodSalt";
//            // Combine deviceId with secret and timestamp
//            long timestamp = System.currentTimeMillis();
//            String data = deviceId + secretKey + timestamp;
//
//            // Create SHA-256 hash
//            MessageDigest digest = MessageDigest.getInstance("SHA-256");
//            byte[] hash = digest.digest(data.getBytes());
//
//            // Encode to Base64
//            return Base64.getEncoder().encodeToString(hash);
//        } catch (NoSuchAlgorithmException e) {
//            throw new RuntimeException("Error generating token", e);
//        }
//    }
}