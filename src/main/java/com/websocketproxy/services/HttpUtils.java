package com.websocketproxy.services;

import com.websocketproxy.repository.UsersSessionManager;
import com.websocketproxy.services.logsandexceptions.MyLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class HttpUtils {

    //singleton
   private final DeviceSessionManager deviceSessionManager = DeviceSessionManager.getInstance();
    //singleton
    private final UsersSessionManager usersSessionManager = UsersSessionManager.getInstance();

    public UsersSessionManager getUsersSessionManager() {
        return usersSessionManager;
    }

//    public HttpUtils(DeviceSessionManager deviceSessionManager) {
//        this.deviceSessionManager = deviceSessionManager;
//    }

    public HttpUtils() {
    }
    public DeviceSessionManager getDeviceSessionManager() {
        return deviceSessionManager;
    }

    public static int getHeadersSize(HttpHeaders headers) {
       int  headersSize=0;

            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                // Размер имени заголовка
                headersSize += entry.getKey().getBytes(StandardCharsets.UTF_8).length;
                // Размер значений заголовка
                for (String value : entry.getValue()) {
                    headersSize += value.getBytes(StandardCharsets.UTF_8).length;
                }
                // Добавляем 4 байта для ": " и "\r\n"
                headersSize += 4;
            }

        MyLogger.logServer("HeadersSize: "+String.valueOf(headersSize));
        return headersSize;
    }


    //  ???  вопрос нужно ли здесь обрезать путь, чтобы на клиенте снова его воссоздавать?
    //возможно нужно ведь на клиенте мы запрашиваем без id устройства а внутренние пути преобразуются уже с id под автозапросы от прокси при загрузки html. это файлы стилей изображений и все внутренние ссылки, они также должны быть с id устройстов чтобы подгружались сами
    public static String getPathFromRequest(HttpServletRequest request, String deviceId) {
        // Получаем оставшуюся часть пути
//        String remainingPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String fullPath = request.getRequestURI();

        // Удаляем часть пути, соответствующую deviceId
        String pathAfterDeviceId = fullPath.substring(fullPath.indexOf("/p/" + deviceId) + ("/p/" + deviceId).length());
        return pathAfterDeviceId;
        // Теперь pathAfterDeviceId содержит оставшуюся часть пути после deviceId
    }

    //перегруженный метод который создаёт заголовок после ответа, например после редиректа 302
    public HashMap<String, String> buildHttpRequest(HashMap<String, String> buildHttpRequest, String setCookie) throws Exception {
        String requestId = buildHttpRequest.keySet().iterator().next();
        String renewRequset = buildHttpRequest.get(requestId);
        StringBuilder requestBuilder = new StringBuilder(renewRequset);
        requestBuilder.append("Set-Cookie: ").append(requestId).append("\n");

        buildHttpRequest.put(requestId,requestBuilder.toString());

        return buildHttpRequest;
    }







        //метод формирует HTTP-запрос (httpRequest)
    public HashMap<String, String> buildHttpRequest(HttpServletRequest request, String deviceId, String sessionId) throws Exception {
        // Генерация уникального requestId для каждого запроса пользователя
        String requestId = this.generateRequestId(deviceId);

        // Получение пути запроса
        String requestPath = this.getPathFromRequest(request, deviceId);

        // Сборка HTTP-запроса с добавлением requestId
        StringBuilder requestBuilder = new StringBuilder();
        requestBuilder.append(request.getMethod()).append(" ");
        // Добавляем путь и query parameters (если есть)
        String queryString = request.getQueryString(); // Получаем query parameters

       ///!!!проверить как формируются гет параметры здесь!!!особенно множественные со знаком &
        String fullPath = requestPath + (queryString != null ? "?" + queryString : "");
        requestBuilder.append(fullPath).append(" HTTP/1.1\n");
        // Добавляем requestId в заголовки
        requestBuilder.append("X-Request-Id: ").append(requestId).append("\n");
        requestBuilder.append("Session-Id: ").append(sessionId).append("\n");
        // Добавляем все остальные поля заголовка
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            requestBuilder.append(headerName).append(": ").append(headerValue).append("\n");
            MyLogger.logServer(headerName, true);
        }
        requestBuilder.append("\n");

        // Создаем мапу и помещаем туда пару ключ-значение
        HashMap<String, String> httpRequestMap = new HashMap<>();
        httpRequestMap.put(requestId, requestBuilder.toString());



        //связываем запрос с клиентской сессией
        //!! также нужно будет освободиться от этого после ответа
        getUsersSessionManager().addRequestToSession(sessionId,requestId);

        return httpRequestMap;
    }


    public static String getUuid() {
        // Генерация уникального UUID
        UUID uniqueKey = UUID.randomUUID();
        return uniqueKey.toString();
    }

    public static String generateRequestId(String deviceId) {
        return deviceId +"|"+ getUuid();
    }

    // Метод для извлечения deviceId из строки
    public static String extractDeviceId(String requestId) {
        // Разделяем строку по символу '|'
        String[] parts = requestId.split("\\|");
        // Возвращаем первый элемент, который является deviceId
        return parts.length > 0 ? parts[0] : null;
    }

    public static byte[] buildMultipartFormData(String boundary, String fieldName, MultipartFile file) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

        // Добавляем границу
        writer.append("--").append(boundary).append("\r\n");

        // Добавляем метаданные файла
        writer.append("Content-Disposition: form-data; name=\"").append(fieldName).append("\"; filename=\"").append(file.getOriginalFilename()).append("\"\r\n");
        writer.append("Content-Type: ").append(file.getContentType()).append("\r\n");
        writer.append("\r\n");
        writer.flush();

        // Добавляем бинарные данные файла
        outputStream.write(file.getBytes());

        // Завершаем часть
        writer.append("\r\n").append("--").append(boundary).append("--\r\n");
        writer.flush();

        return outputStream.toByteArray();
    }






    public static Set<String> getSupportedCompressionMethods(HttpServletRequest request) {
        String acceptEncodingHeader = request.getHeader("Accept-Encoding");
        if (acceptEncodingHeader == null || acceptEncodingHeader.isEmpty()) {
            return Collections.emptySet();
        }

        // Разделяем заголовок по запятым и удаляем пробелы
        String[] encodings = acceptEncodingHeader.split("\\s*,\\s*");

        // Преобразуем в список и удаляем параметры качества (например, gzip;q=0.8)
        return Arrays.stream(encodings)
                .map(encoding -> encoding.split(";")[0].trim().toLowerCase())
                .collect(Collectors.toSet());
    }


}