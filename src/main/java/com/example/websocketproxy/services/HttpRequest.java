package com.example.websocketproxy.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.websocketproxy.services.logsandexceptions.MyLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

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







        public static String getUuid() {
            // Генерация уникального UUID
            UUID uniqueKey = UUID.randomUUID();
            return uniqueKey.toString();
        }

    public static String generateRequestId(String deviceId) {
        return deviceId +"|"+ getUuid();
    }


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


    //метод формирует HTTP-запрос (httpRequest)
    public Map<String, String> buildHttpRequest(HttpServletRequest request, String deviceId) throws Exception {
        // Генерация уникального requestId для каждого запроса пользователя
        String requestId = this.generateRequestId(deviceId);

        // Получение пути запроса
        String requestPath = this.getPathFromRequest(request, deviceId);

        // Сборка HTTP-запроса с добавлением requestId
        StringBuilder requestBuilder = new StringBuilder();
        requestBuilder.append(request.getMethod()).append(" ");
        // Добавляем путь и query parameters (если есть)
        String queryString = request.getQueryString(); // Получаем query parameters
        String fullPath = requestPath + (queryString != null ? "?" + queryString : "");
        requestBuilder.append(fullPath).append(" HTTP/1.1\n");
        // Добавляем requestId в заголовки
        requestBuilder.append("X-Request-Id: ").append(requestId).append("\n");

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
        Map<String, String> httpRequestMap = new HashMap<>();
        httpRequestMap.put(requestId, requestBuilder.toString());

        return httpRequestMap;
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

}
