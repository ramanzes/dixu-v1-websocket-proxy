package com.websocketproxy.repository;


import com.websocketproxy.repository.database.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.Optional;


public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    DeviceToken findByDeviceId(String deviceId);
    boolean existsByDeviceId(String deviceId);
    void deleteByDeviceId(String deviceId);

    // Получение максимального ID (вариант 1 - через HQL)
    @Query("SELECT MAX(d.id) FROM DeviceToken d")
    Optional<Long> findMaxId();

    // Получение максимального номера из deviceId (формат "deviceId-{N}")
    @Query("SELECT MAX(CAST(SUBSTRING(d.deviceId, 9) AS long)) FROM DeviceToken d WHERE d.deviceId LIKE 'device-%'")
    Optional<Long> findMaxDeviceIdNumber();

    // Получение последней записи (вариант 2 - через сортировку)
    @Query("SELECT d FROM DeviceToken d ORDER BY d.id DESC LIMIT 1")
    Optional<DeviceToken> findTopByOrderByIdDesc();




}

