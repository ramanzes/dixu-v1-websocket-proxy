package com.websocketproxy.services;


import com.websocketproxy.repository.DeviceTokenRepository;
import com.websocketproxy.repository.database.DeviceToken;
import com.websocketproxy.services.logsandexceptions.MyLogger;
import com.websocketproxy.services.logsandexceptions.exceptions.MyOtherExceptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
public class DeviceTokenService {

    private final DeviceTokenRepository repository;

    public DeviceTokenService(DeviceTokenRepository repository) {
        this.repository = repository;
    }

//    @Transactional
////    public void saveToken(String deviceId, String token) {
//    public void saveToken() {
//        if (repository.existsByDeviceId(deviceId)) {
//            throw new IllegalArgumentException("Device ID already exists");
//        }
//
////        DeviceToken deviceToken = new DeviceToken(deviceId, token);
//        DeviceToken deviceToken = new DeviceToken();
//        repository.save(deviceToken);
//    }

    @Transactional(readOnly = true)
    public String getTokenByDeviceId(String deviceId) {
        DeviceToken deviceToken = repository.findByDeviceId(deviceId);

        MyLogger.logServer("deviceId = "+deviceId);
        MyLogger.logServer("token = "+deviceToken);
        return deviceToken != null ? deviceToken.getToken() : null;
    }

    @Transactional
    private void deleteByDeviceId(String deviceId) {
        repository.deleteByDeviceId(deviceId);
    }

    @Transactional(readOnly = true)
    public boolean deviceExists(String deviceId) {
        return repository.existsByDeviceId(deviceId);
    }


    /**
     * Validates a token for a specific deviceId
     *
     * @param deviceId The device identifier
     * @param token The token to validate
     * @return true if token is valid, false otherwise
     */
    public boolean validateToken(String deviceId, String token) {

        return getTokenByDeviceId(deviceId).equals(token);

    }

//    @Transactional
//    public DeviceToken createNewDeviceToken() {
//        DeviceToken deviceToken = new DeviceToken();
//        return repository.save(deviceToken);
//    }

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

//    @Autowired
//    private TokenAuthenticationService tokenService;


    public DeviceToken createNewDeviceToken() {
        long newNumber = deviceTokenRepository.findMaxId().orElse(0L)+1;
//                findMaxDeviceIdNumber().orElse(0L) + 1;
        String newDeviceId = "device-" + newNumber;

        String token = generateAndStoreToken(newDeviceId);

        DeviceToken deviceToken = new DeviceToken(newDeviceId,token);
//        deviceToken.setDeviceId(newDeviceId);
//        deviceToken.setToken(token);

        return deviceTokenRepository.save(deviceToken);
    }


    /**
     * Generates and stores a new token for a device
     *
     * @param deviceId The device identifier
     * @return The generated token
     */
    public static String generateAndStoreToken(String deviceId) {
        // Generate a unique token
        String token = generateToken(deviceId);
        return token;
    }

    /**
     * Revokes a token for a device
     *
     * @param deviceId The device identifier
     */
    public void revokeToken(String deviceId) {
//        deviceTokenMap.remove(deviceId);
    }

    /**
     * Generates a token for a device using HMAC-SHA256
     *
     * @param deviceId The device identifier
     * @return Generated token
     */
    private static String generateToken(String deviceId) {
        try {
            String secretKey = "goodSalt";
            // Combine deviceId with secret and timestamp
            long timestamp = System.currentTimeMillis();
            String data = deviceId + secretKey + timestamp;

            // Create SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());

            // Encode to Base64
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating token", e);
        }
    }


}