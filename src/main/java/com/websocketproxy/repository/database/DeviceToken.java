package com.websocketproxy.repository.database;


import com.websocketproxy.services.DeviceTokenService;
import com.websocketproxy.services.TokenAuthenticationService;
import jakarta.persistence.*;

@Entity
@Table(name = "device_tokens",
        uniqueConstraints = @UniqueConstraint(columnNames = "deviceId"))
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deviceId;  // например "deviceid-1"

    @Column(nullable = false)
    private String token;


// Конструкторы, геттеры и сеттеры
//    public DeviceToken() {
//    }

    public DeviceToken() {
    }

    public DeviceToken(String deviceId, String token) {
        this.deviceId = deviceId;
        this.token = token;
//        this.deviceId = "deviceId-"+ Long.toString(getId());
//        this.token = TokenAuthenticationService.generateAndStoreToken(this.deviceId);
    }

    // Геттеры и сеттеры
    public Long getId() {
        return this.id;
    }

//    public void setId(Long id) {
//        this.id = id;
//    }

    public String getDeviceId() {
        return deviceId;
    }

//    public void setDeviceId(String deviceId) {
//        this.deviceId = deviceId;
//    }

    public String getToken() {
        return token;
    }

    public void setDeviceId(String newDeviceId) {
        this.deviceId=newDeviceId;
    }



    public void setToken(String token) {
        this.token = token;
    }
}
