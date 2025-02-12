package com.example.websocketproxy.services;

import com.example.websocketproxy.services.logsandexceptions.MyLogger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        return modifiedContent.toString();
    }


}
