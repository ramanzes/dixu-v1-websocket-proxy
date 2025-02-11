package com.example.websocketproxy.services;

import com.example.websocketproxy.services.logsandexceptions.MyLogger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public class HttpResponse {

    // Метод для извлечения заголовков
    public static HttpHeaders extractHeaders(String textResponse) {
        HttpHeaders headers = new HttpHeaders();
        if (textResponse.contains("\r\n\r\n")) {
            String[] parts = textResponse.split("\r\n\r\n", 2);
            String headerLines = parts[0];

            // Разделяем заголовки на строки и добавляем в HttpHeaders
            String[] headerArray = headerLines.split("\r\n");
            for (String headerLine : headerArray) {
                // Пропускаем первую строку (статус)
                if (headerLine.startsWith("HTTP/")) {
                    continue;
                }
                String[] header = headerLine.split(": ", 2);
                if (header.length == 2) {
                    headers.add(header[0], header[1]);
                }
            }
        }
        return headers;
    }

    // Метод для извлечения тела
    public static String extractBody(String textResponse) {
        if (textResponse.contains("\r\n\r\n")) {
            String[] parts = textResponse.split("\r\n\r\n", 2);
            return parts.length > 1 ? parts[1] : "";
        }
        return "";
    }
    public static boolean isTextResponse(String contentType) {
        // Проверяем, текстовые ли это данные
        boolean isTextResponse = contentType != null && contentType.startsWith("text/") ||
                contentType != null && contentType.contains("javascript") ||
                contentType != null && contentType.contains("json") ||
                contentType != null && contentType.contains("xml");
        return isTextResponse;
    }

    public static boolean isGzipped(HttpHeaders header) {
        // Проверяем, сжат ли контент
        boolean isGzipped = ("gzip".equalsIgnoreCase(header.getFirst("Content-Encoding")));
        MyLogger.logServer("Контент ресурса сжат? = ["+isGzipped+"]", true);
        return isGzipped;
    }

}
