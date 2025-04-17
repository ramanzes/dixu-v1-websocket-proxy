package com.websocketproxy.services;

import java.util.Map;

import com.websocketproxy.services.logsandexceptions.MyLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.http.Cookie;




@Component
//класс получения доступной информации по пути запроса
public class HttpRequest {
//метод для извлечения кук:
    public static Map<String, String> extractCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        Map<String, String> cookieMap = new ConcurrentHashMap<>();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                cookieMap.put(cookie.getName(), cookie.getValue());
            }
        }
        return cookieMap;
    }



    public static boolean isHtmlPageRequest(String resourcePath) {
        // Если путь заканчивается на "/" или на ".html", это запрос к HTML-странице  иначе бинарник
        return resourcePath.isEmpty() ||
                resourcePath.equals("/") ||
                resourcePath.endsWith(".html") ||
                resourcePath.endsWith(".htm") ||
                resourcePath.endsWith(".js") ||
                resourcePath.endsWith(".css") ||
                !resourcePath.contains(".");
    }


    public static String getContentType(String resourcePath) {
        if (resourcePath.equals("")) {
            MyLogger.logServer(resourcePath + " text",true);
            return "text/html";
        } else if (!resourcePath.contains(".")) { // Проверка на отсутствие расширения
            MyLogger.logServer(resourcePath + " directory",true);
            return "text/html"; // Возвращаем text/html для путей без расширения
        } else if (resourcePath.endsWith(".tmpl")) {
            MyLogger.logServer(resourcePath + " text",true);
            return "text/css";
        } else if (resourcePath.endsWith(".htm")) {

            MyLogger.logServer(resourcePath + " text",true);
            return "text/html";
        } else if (resourcePath.endsWith(".html")) {

            MyLogger.logServer(resourcePath + " text",true);
            return "text/html";
        } else if (resourcePath.endsWith(".css")) {

            MyLogger.logServer(resourcePath + " text",true);
            return "text/css";
        } else if (resourcePath.endsWith(".js")) {

            MyLogger.logServer(resourcePath + " js",true);
            return "application/javascript";
        } else if (resourcePath.endsWith(".png")) {

            MyLogger.logServer(resourcePath + " png",true);
            return "image/png";
        } else if (resourcePath.endsWith(".jpg") || resourcePath.endsWith(".jpeg")) {

            MyLogger.logServer(resourcePath + " jpeg",true);
            return "image/jpeg";
        } else if (resourcePath.endsWith(".gif")) {

            MyLogger.logServer(resourcePath + " gif",true);
            return "image/gif";
        } else if (resourcePath.endsWith(".svg")) {

            MyLogger.logServer(resourcePath + " svg",true);
            return "image/svg+xml";
        } else if (resourcePath.endsWith(".webp")) {
            MyLogger.logServer(resourcePath + " image/webp",true);
            return "image/webp";
        } else if (resourcePath.endsWith(".ico")) {
            MyLogger.logServer(resourcePath + " ico",true);
            return "image/x-icon";
        } else if (resourcePath.endsWith(".woff")) {

            MyLogger.logServer(resourcePath + " woff",true);
            return "font/woff";
        } else if (resourcePath.endsWith(".woff2")) {

            MyLogger.logServer(resourcePath + " woff2",true);
            return "font/woff2";
        } else if (resourcePath.endsWith(".ttf")) {

            MyLogger.logServer(resourcePath + " tft",true);
            return "font/ttf";
        } else if (resourcePath.endsWith(".eot")) {

            MyLogger.logServer(resourcePath + " fontonject",true);
            return "application/vnd.ms-fontobject";
        } else if (resourcePath.endsWith(".otf")) {

            MyLogger.logServer(resourcePath + " otf",true);
            return "font/otf";
        } else if (resourcePath.endsWith(".json")) {
            MyLogger.logServer(resourcePath + " .json",true);
            return "application/json";
        } else if (resourcePath.endsWith(".xml")) {
            MyLogger.logServer(resourcePath + " .",true);
            return "application/xml";
        } else if (resourcePath.endsWith(".txt")) {
            MyLogger.logServer(resourcePath + " .txt",true);
            return "text/plain";
        } else if (resourcePath.endsWith(".pdf")) {
            MyLogger.logServer(resourcePath + " .pdf",true);
            return "application/pdf";
        } else if (resourcePath.endsWith(".zip")) {
            MyLogger.logServer(resourcePath + " .zip",true);
            return "application/zip";
        } else if (resourcePath.endsWith(".tar")) {
            MyLogger.logServer(resourcePath + " .tar",true);
            return "application/x-tar";
        } else if (resourcePath.endsWith(".gz")) {
            MyLogger.logServer(resourcePath + " .gzip",true);
            return "application/gzip";
        } else if (resourcePath.endsWith(".mp3")) {
            MyLogger.logServer(resourcePath + " .mp3",true);
            return "audio/mpeg";
        } else if (resourcePath.endsWith(".wav")) {
            MyLogger.logServer(resourcePath + " .wav",true);
            return "audio/wav";
        } else if (resourcePath.endsWith(".mp4")) {
            MyLogger.logServer(resourcePath + " .mp4",true);
            return "video/mp4";
        } else if (resourcePath.endsWith(".webm")) {
            MyLogger.logServer(resourcePath + " .webm",true);
            return "video/webm";
        } else if (resourcePath.endsWith(".ogg")) {
            MyLogger.logServer(resourcePath + " .ogg",true);
            return "audio/ogg";
        } else if (resourcePath.endsWith(".webp")) {
            MyLogger.logServer(resourcePath + " .webb",true);
            return "image/webp";
        } else if (resourcePath.endsWith(".csv")) {
            MyLogger.logServer(resourcePath + " .csv",true);
            return "text/csv";
        } else if (resourcePath.endsWith(".doc")) {
            MyLogger.logServer(resourcePath + " .doc",true);
            return "application/msword";
        } else if (resourcePath.endsWith(".docx")) {
            MyLogger.logServer(resourcePath + " .docx",true);
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (resourcePath.endsWith(".xls")) {
            MyLogger.logServer(resourcePath + " .xls",true);
            return "application/vnd.ms-excel";
        } else if (resourcePath.endsWith(".xlsx")) {
            MyLogger.logServer(resourcePath + " .xlsx",true);
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (resourcePath.endsWith(".ppt")) {
            MyLogger.logServer(resourcePath + " .ppt",true);
            return "application/vnd.ms-powerpoint";
        } else if (resourcePath.endsWith(".pptx")) {
            MyLogger.logServer(resourcePath + " .pptx",true);
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        } else {
            MyLogger.logServer(resourcePath + "неизвестное расширение возвращаем application/octet-stream");
            return "application/octet-stream"; // По умолчанию для неизвестных типов
        }


    }

//заменить на этот метод
//
//    private static final Map<String, String> MIME_TYPES = Map.of(
//            ".html", "text/html",
//            ".css", "text/css",
//            ".js", "application/javascript",
//            ".png", "image/png",
//            ".jpg", "image/jpeg",
//            ".woff", "font/woff"
//            // Add more mappings
//    );
//
//    public static String getContentType(String resourcePath) {
//        return MIME_TYPES.entrySet()
//                .stream()
//                .filter(entry -> resourcePath.endsWith(entry.getKey()))
//                .map(Map.Entry::getValue)
//                .findFirst()
//                .orElse("application/octet-stream");
//    }
//








//    public static String getContentTypeFromBinaryResponse(ByteArrayOutputStream buffer) throws Exception {
//        byte[] fullMessageBytes = buffer.toByteArray();
//        buffer.reset(); // Очищаем буфер
//
//        // Преобразуем байты в строку для заголовков
//        String fullMessage = new String(fullMessageBytes, StandardCharsets.UTF_8);
//
//        // Разделяем заголовки и данные
//        int headerEndIndex = fullMessage.indexOf("\r\n\r\n");
//        if (headerEndIndex == -1) return "application/octet-stream";;
//        String headers = fullMessage.substring(0, headerEndIndex);
//        byte[] data = Arrays.copyOfRange(fullMessageBytes, headerEndIndex + 4, fullMessageBytes.length);
//
//        // Извлекаем Content-Type из заголовков
//        String contentType = extractContentType(headers);
//        return contentType;
//    }
//

//    /**
//     * Извлекает Content-Type из заголовков.
//     */
//    private static String extractContentType(String headers) {
//        for (String line : headers.split("\r\n")) {
//            if (line.startsWith("Content-Type:")) {
//                return line.substring("Content-Type:".length()).trim();
//            }
//        }
//        return "application/octet-stream"; // По умолчанию
//    }
//








//    private static final Map<String, String> contentTypeForRequestId = new ConcurrentHashMap<>();

//    public static void addContentTypeForRequestId(String requestId, String contentType) {
//        contentTypeForRequestId.put(requestId, contentType);
//    }
//
//    public static String getContentTypeFromRequestId(String requestId) {
//        return contentTypeForRequestId.get(requestId);
//    }
//
//    public static void removeContentTypeRequestId(String requestId) {
//        contentTypeForRequestId.remove(requestId);
//    }








}
