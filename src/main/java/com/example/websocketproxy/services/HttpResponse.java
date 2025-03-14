package com.example.websocketproxy.services;

import com.example.websocketproxy.services.logsandexceptions.MyLogger;
import com.example.websocketproxy.services.logsandexceptions.exceptions.MyOtherExceptions;
import com.example.websocketproxy.websocket.WebSocketProxyHandler;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.websocketproxy.services.MyWebsocketUtils.decompress;

@Component
public class HttpResponse {


//перенести в HttpUtils
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

    //перенести в HttpUtils
// метод, который извлекает код ответа из заголовков
    public static int extractStatusCode(String textResponse) {
        if (textResponse.contains("\r\n")) {
            // Получаем первую строку заголовков
            String[] lines = textResponse.split("\r\n");
            String statusLine = lines[0];

            // Разделяем строку статуса по пробелам
            String[] statusParts = statusLine.split(" ");
            if (statusParts.length >= 2) {
                try {
                    // Возвращаем код ответа (второй элемент)
                    return Integer.parseInt(statusParts[1]);
                } catch (NumberFormatException e) {
                    // Обработка ошибки, если код не может быть преобразован в int
                    e.printStackTrace();
                }
            }
        }
        // Возвращаем -1, если код не найден
        return -1;
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



    /**
     * Модифицирует пути в HTML, CSS и JS контенте, добавляя префикс "/p/{deviceid}/" к ссылкам.
     */
    public static String modifyHtmlPaths(String content, String contentType, String diviceId) {
        // Определяем, какой тип контента обрабатываем
        boolean isHtml = contentType.startsWith("text/html");
        boolean isCss = contentType.startsWith("text/css");
        boolean isJs = contentType.contains("javascript");


        // Регулярные выражения для поиска путей
//        String htmlRegex = "(href|src|background-image)\\s*=\\s*[\"']([^\"']+)[\"']";
        String htmlRegex = "(href|src|background-image|action)\\s*=\\s*[\"']([^\"']+)[\"']";
        String cssRegex = "url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)";
        String jsRegex = "['\"](/[^'\"]+)['\"]";
        String jsVarRegex = "(const|let|var)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*[\"'](/[^\"']+)[\"'];";

        // Если тип контента не поддерживается, возвращаем контент без изменений
        if (!isHtml && !isCss && !isJs) {
            MyLogger.logServer("неизвестный тип контента");
            return content;
        }

        // Если это HTML, обрабатываем его как HTML, CSS и JS
        if (isHtml) {
            content = applyRegex(content, htmlRegex, "html",diviceId);
            content = applyRegex(content, cssRegex, "css",diviceId);
            content = applyRegex(content, jsVarRegex, "js-var",diviceId);
//            content = applyRegex(content, jsRegex, "js");
        } else if (isCss) {
            // Если это CSS, обрабатываем только CSS
            content = applyRegex(content, cssRegex, "css",diviceId);
        } else if (isJs) {
            // Если это JS, обрабатываем только JS
            content = applyRegex(content, jsRegex, "js",diviceId);
        }

        return content;
    }

    private static String applyRegex(String content, String regex, String type, String deviceId) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);

        StringBuffer modifiedContent = new StringBuffer();

        while (matcher.find()) {
            String path;
            if ("html".equals(type)) {
                // Для HTML извлекаем путь из атрибутов href, src, background-image и action
                path = matcher.group(2);
            } else if ("js-var".equals(type)) {
                // Для JS-переменных извлекаем путь из группы 3
                path = matcher.group(3);
            } else {
                // Для CSS и JS извлекаем путь из группы 1
                path = matcher.group(1);
            }

            // Если путь начинается с "/" и не является абсолютным URL
            if (path.startsWith("/") && !path.startsWith("http://") && !path.startsWith("https://") && !path.startsWith("//")) {
                // Добавляем префикс
                String newPath = "/p/" +  deviceId + path;

                // Заменяем старое значение на новое
                if ("html".equals(type)) {
                    matcher.appendReplacement(modifiedContent, matcher.group(1) + "=\"" + newPath + "\"");
                } else if ("css".equals(type)) {
                    matcher.appendReplacement(modifiedContent, "url('" + newPath + "')");
                } else if ("js".equals(type)) {
                    matcher.appendReplacement(modifiedContent, "'" + newPath + "'");
                } else if ("js-var".equals(type)) {
                    matcher.appendReplacement(modifiedContent, matcher.group(1) + " " + matcher.group(2) + " = \"" + newPath + "\";");
                }
            } else {
                // Оставляем путь без изменений
                matcher.appendReplacement(modifiedContent, matcher.group(0));
            }
        }

        // Добавляем оставшуюся часть строки
        matcher.appendTail(modifiedContent);

//        MyLogger.logServer(modifiedContent.toString());

        return modifiedContent.toString();
    }



    //метод для обработки ответа устройства
    public ResponseEntity<?> processDeviceResponse(String requestId, String deviceId, WebSocketProxyHandler webSocketProxyHandler, MyWebsocketUtils myWebsocketUtils) throws Exception {
        // Ждем ответ от устройства
        CompletableFuture<String> textResponseFuture = webSocketProxyHandler.waitForResponse(requestId);
        //ассинхронно дожидаемся получения всех данных по отправленному с контроллера запроса

        String textResponse = textResponseFuture.get(20, TimeUnit.SECONDS);
//        String textResponse = textResponseFuture.get();

        // Извлекаем заголовки и тело текстового ответа
        HttpHeaders responseHeaders = HttpResponse.extractHeaders(textResponse);
        String contentType = responseHeaders.getFirst(HttpHeaders.CONTENT_TYPE);
        String responseTextBody = HttpResponse.extractBody(textResponse);
        int statusCode = HttpResponse.extractStatusCode(textResponse);

        byte[] binaryResponse = null;

//        if (responseTextBody.length() == 0 && statusCode != 304 && statusCode != 204 && statusCode != 205) {
        if (responseTextBody.length() == 0) {
            MyLogger.logServer("Ответ содержит только заголовки." + " значит ждём и бинарные данные");
            // Получаем бинарные данные для того же запроса
            CompletableFuture<byte[]> binaryResponseFuture = webSocketProxyHandler.waitForBinaryResponse(requestId);
            binaryResponse = binaryResponseFuture.get(20, TimeUnit.SECONDS);

//            binaryResponse = binaryResponseFuture.get();
        }

        if (binaryResponse == null) {
            // Текстовый ответ
            MyLogger.logServer("возвращаем текстовый ответ от клиента");

            int headerLength = responseHeaders.toString().getBytes().length;
            String updateBody = HttpResponse.modifyHtmlPaths(responseTextBody, contentType, deviceId);

            //в кодировке UTF-8 некоторые символы (например, символы из других языков или специальные символы) могут занимать более одного байта.
            byte[] bodyBytes = updateBody.getBytes(StandardCharsets.UTF_8);

            int newContentLength = headerLength + bodyBytes.length;

//            int newContentLength = bodyBytes.length;

            responseHeaders.setContentLength(newContentLength);
//            responseHeaders.remove("Content-Length");
//            responseHeaders.set("Transfer-Encoding", "chunked");

            return ResponseEntity.ok()
                    .headers(responseHeaders)
                    .body(updateBody.getBytes(StandardCharsets.UTF_8));
        } else {
            try {
                MyLogger.logServer("Возвращаем бинарный ответ от клиента, contentType: " + contentType, true);

                MediaType mediaType;
                try {
                    mediaType = MediaType.valueOf(contentType);
                } catch (InvalidMediaTypeException e) {
                    throw new IllegalArgumentException("Некорректный contentType: " + contentType, e);
                }

                Object body = null;
                if (myWebsocketUtils.isCompressed(responseHeaders)) {
                    MyLogger.logServer("Данные сжаты, разжимаем...:\n");
                    body = decompress(binaryResponse, responseHeaders);
//после распаковки убираем в заголовках отметки о том что контент сжат
                    responseHeaders.remove("Content-Encoding");
                    responseHeaders.remove("Transfer-Encoding");
                } else {
                    body = binaryResponse;
                }

                if (body instanceof String) {
                    String updateBody = HttpResponse.modifyHtmlPaths((String) body, contentType, deviceId);
                    byte[] textBytes = updateBody.getBytes(StandardCharsets.UTF_8);

                    //после распаковки длина контента работает только без учёта заголовков
                    responseHeaders.setContentLength(textBytes.length);
                    long  contentLength = (long)(HttpUtils.getHeadersSize(responseHeaders) + textBytes.length);
//                    responseHeaders.setContentLength(contentLength);
                    MyLogger.logServer("размер после распаковки без заголовков Content-Length "+String.valueOf(textBytes.length));
                    MyLogger.logServer("размер после распаковки с заголовками Content-Length "+contentLength);

                    return ResponseEntity.ok()
                            .headers(responseHeaders)
                            .body(textBytes);
                } else {
                    return ResponseEntity.ok()
                            .headers(responseHeaders)
                            .contentType(mediaType)
                            .body((byte[]) body);
                }
            } catch (Exception e) {
                MyLogger.logServer("Ошибка при формировании ответа: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error while processing binary response".getBytes(StandardCharsets.UTF_8));
            }
        }
    }


}
