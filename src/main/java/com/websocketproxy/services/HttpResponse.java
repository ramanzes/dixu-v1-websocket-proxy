package com.websocketproxy.services;

import com.websocketproxy.repository.Devices;
import com.websocketproxy.services.logsandexceptions.MyLogger;
import com.websocketproxy.websocket.WebSocketProxyHandler;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

import java.net.URI;
//import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.websocketproxy.services.MyWebsocketUtils.decompress;

@Component
public class HttpResponse {

  // перенести в HttpUtils
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

  // перенести в HttpUtils
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
          return whatStatusCode(statusParts);
          // Возвращаем код ответа (второй элемент)
          // return parseInt(statusParts[1]);
        } catch (NumberFormatException e) {
          // Обработка ошибки, если код не может быть преобразован в int
          e.printStackTrace();
        }
      }
    }
    // Возвращаем -1, если код не найден
    return -1;
  }

  protected static Integer whatStatusCode(String[] status) {
    for (int i = 0; i < status.length; i++) {
      try {
        return Integer.parseInt(status[i]);
      } catch (NumberFormatException e) {
        // Игнорируем, если строка не может быть преобразована в число
      }
    }
    return null;
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
    MyLogger.logServer("Контент ресурса сжат? = [" + isGzipped + "]", true);
    return isGzipped;
  }

  /**
   * Модифицирует пути в HTML, CSS и JS контенте, добавляя префикс
   * "/p/{deviceid}/" к ссылкам.
   */
  public static String modifyHtmlPaths(String content, String contentType, String deviceId) {
    // Определяем, какой тип контента обрабатываем
    boolean isHtml = contentType.startsWith("text/html");
    boolean isCss = contentType.startsWith("text/css");
    boolean isJs = contentType.contains("javascript");

    // Регулярные выражения для поиска путей
    // String htmlRegex = "(href|src|background-image)\\s*=\\s*[\"']([^\"']+)[\"']";
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
      content = applyRegex(content, htmlRegex, "html", deviceId);
      content = applyRegex(content, cssRegex, "css", deviceId);
      content = applyRegex(content, jsVarRegex, "js-var", deviceId);
      // content = applyRegex(content, jsRegex, "js");
    } else if (isCss) {
      // Если это CSS, обрабатываем только CSS
      content = applyRegex(content, cssRegex, "css", deviceId);
    } else if (isJs) {
      // Если это JS, обрабатываем только JS
      content = applyRegex(content, jsRegex, "js", deviceId);
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
      if (path.startsWith("/") && !path.startsWith("http://") && !path.startsWith("https://")
          && !path.startsWith("//")) {
        // Добавляем префикс
        String newPath = "/p/" + deviceId + path;

        // Заменяем старое значение на новое
        if ("html".equals(type)) {
          matcher.appendReplacement(modifiedContent, matcher.group(1) + "=\"" + newPath + "\"");
        } else if ("css".equals(type)) {
          matcher.appendReplacement(modifiedContent, "url('" + newPath + "')");
        } else if ("js".equals(type)) {
          matcher.appendReplacement(modifiedContent, "'" + newPath + "'");
        } else if ("js-var".equals(type)) {
          matcher.appendReplacement(modifiedContent,
              matcher.group(1) + " " + matcher.group(2) + " = \"" + newPath + "\";");
        }
      } else {
        // Оставляем путь без изменений
        matcher.appendReplacement(modifiedContent, matcher.group(0));
      }
    }

    // Добавляем оставшуюся часть строки
    matcher.appendTail(modifiedContent);

    // MyLogger.logServer(modifiedContent.toString());

    return modifiedContent.toString();
  }

  // проверка статусов ответа на редиректность

  private boolean isThisRedirect(int statusCode) {
    /**
     * Кроме этих основных кодов, существуют и другие коды 3xx, но они менее
     * распространены:
     * 300 Multiple Choices: Указывает, что существует несколько вариантов ресурса,
     * и клиент должен выбрать один из них.
     * 301 Moved Permanently: Указывает, что ресурс был перемещен на постоянной
     * основе на новый URL.
     * 302 Found: Указывает, что ресурс временно доступен по другому URL.
     * 303 See Other: Указывает, что клиент должен сделать GET-запрос по другому
     * URL.
     * 307 Temporary Redirect: Указывает, что ресурс временно доступен по другому
     * URL, и клиент должен использовать тот же метод для нового запроса.
     * 308 Permanent Redirect: Указывает, что ресурс был перемещен на постоянной
     * основе, и клиент должен использовать тот же метод для нового запроса.
     * Важно отметить, что не все браузеры и клиенты могут обрабатывать все коды 3xx
     * одинаково, и поведение может варьироваться в зависимости от реализации.
     */
    HashSet<Integer> setStatuses = new HashSet<>();
    setStatuses.add(300);
    setStatuses.add(301);
    setStatuses.add(302);
    setStatuses.add(303);
    setStatuses.add(307);
    setStatuses.add(308);
    if (setStatuses.contains(statusCode))
      return true;
    return false;
  }

  public ResponseEntity processDeviceResponse(String requestId, WebSocketProxyHandler webSocketProxyHandler,
      MyWebsocketUtils myWebsocketUtils) throws Exception {
    // Ждем ответ от устройства
    CompletableFuture<String> textResponseFuture = webSocketProxyHandler.waitForResponse(requestId);
    // ассинхронно дожидаемся получения всех данных по отправленному с контроллера
    // запроса
    // на проде нужно добавить этот лимит ожидания!!!!
    // String textResponse = textResponseFuture.get(120, TimeUnit.SECONDS);
    String textResponse = textResponseFuture.get();

    // здесь я имею первые заголовки ответа по которым можно сказать какие методы
    // сжатия поддерживает устройство
    // Извлекаем заголовки и тело текстового ответа
    HttpHeaders responseHeaders = HttpResponse.extractHeaders(textResponse);
    String deviceId = HttpUtils.extractDeviceId(requestId);
    Devices thisDevice = myWebsocketUtils.getDeviceSessionManager().getThisDevice(deviceId);

    // Определяем методы сжатия для текущего ответа
    Set<String> methodThisResponseCompress = myWebsocketUtils.getSupportedCompressionMethods(responseHeaders);
    // устанавливаем устройству флаг (не)/поддержки сжатия. только в том случае если
    // флаг для устройства ещё не был ни в одном из ответов установлен.
    boolean thisResponseCompress = !methodThisResponseCompress.isEmpty();

    // если стоят дефолтные параметры т.е. запускаем метод установки новых значений
    if (!thisDevice.getLocalservWithCompress() && thisResponseCompress) {
      // ТУТ ЗНАЧЕНИЕ thisDevice.getLocalservWithCompress() МОЖЕТ В ПЕРВЫЙ И
      // ЕДИНСТВЕННЫЙ РАЗ ИЗМЕНИТЬСЯ
      thisDevice.setMethodCompress(methodThisResponseCompress);
    }

    String contentType = responseHeaders.getFirst(HttpHeaders.CONTENT_TYPE);
    String responseTextBody = HttpResponse.extractBody(textResponse);
    int statusCode = HttpResponse.extractStatusCode(textResponse);

    // Проверка наличия/действительности Content-Encoding заголовка - НОВАЯ ПРОВЕРКА
    String contentEncoding = responseHeaders.getFirst(HttpHeaders.CONTENT_ENCODING);
    // Передаем Content-Encoding только если ответ действительно сжат
    if (!thisResponseCompress && contentEncoding != null) {
      // Если ответ не сжат, но заголовок присутствует - это может вызвать ошибку в
      // браузере
      responseHeaders.remove(HttpHeaders.CONTENT_ENCODING);
    }

    byte[] binaryResponse = null;
    // !!! разобраться с кэшированными данными и их безошибочным проксированием
    // if (responseTextBody.length() == 0 && statusCode != 304 && statusCode != 204
    // && statusCode != 205) {
    if (responseTextBody.length() == 0 && !isThisRedirect(statusCode) && statusCode != 304 && statusCode < 400) {
      MyLogger.logServer("Ответ содержит только заголовки." + " значит ждём и бинарные данные");
      // Получаем бинарные данные для того же запроса
      CompletableFuture<byte[]> binaryResponseFuture = webSocketProxyHandler.waitForBinaryResponse(requestId);
      // binaryResponse = binaryResponseFuture.get(120, TimeUnit.SECONDS);
      binaryResponse = binaryResponseFuture.get();
    }

    if (binaryResponse == null) {
      // Текстовый ответ
      MyLogger.logServer("возвращаем текстовый ответ от клиента");
      String updateBody = responseTextBody;

      byte[] bodyBytes = updateBody.getBytes(StandardCharsets.UTF_8);
      int newContentLength = bodyBytes.length;
      responseHeaders.setContentLength(newContentLength);

      // Обработка заголовка Location для редиректов
      List<String> locationValues = responseHeaders.get(HttpHeaders.LOCATION);
      if (locationValues != null && !locationValues.isEmpty()) {
        String location = locationValues.get(0);
        // Префикс добавляем только если Location не начинается с /p/{deviceId}
        if (!location.startsWith("/p/" + deviceId)) {
          responseHeaders.setLocation(URI.create("/p/" + deviceId + location));
        }
      }

      return ResponseEntity.status(statusCode)
          .headers(responseHeaders)
          .body(bodyBytes);
    } else {
      try {
        MyLogger.logServer("Возвращаем бинарный ответ от клиента, contentType: " + contentType, true);
        MediaType mediaType;
        try {
          mediaType = MediaType.valueOf(contentType);
        } catch (InvalidMediaTypeException e) {
          // Если Content-Type некорректный, используем application/octet-stream
          mediaType = MediaType.APPLICATION_OCTET_STREAM;
          MyLogger.logServer("Некорректный contentType: " + contentType + ", используем application/octet-stream");
        }

        byte[] body = binaryResponse;

        // В случае если ответ сжат, просто передаем его как есть,
        // включая оригинальные заголовки сжатия (Content-Encoding)
        if (thisResponseCompress) {
          MyLogger.logServer("Данные сжаты, передаем как есть с сохранением Content-Encoding");
          // Проверяем, соответствует ли указанный Content-Encoding доступным методам
          // сжатия
          if (contentEncoding != null && !methodThisResponseCompress.contains(contentEncoding)) {
            MyLogger.logServer("Внимание: Content-Encoding '" + contentEncoding +
                "' не соответствует поддерживаемым методам: " + methodThisResponseCompress);
          }
        }

        // Установка правильного Content-Length
        responseHeaders.setContentLength(body.length);

        return ResponseEntity.status(statusCode)
            .headers(responseHeaders)
            .contentType(mediaType)
            .body(body);
      } catch (Exception e) {
        MyLogger.logServer("Ошибка при формировании ответа: " + e.getMessage());
        e.printStackTrace(); // Добавлено для лучшей диагностики
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("Error while processing binary response".getBytes(StandardCharsets.UTF_8));
      }
    }
  }

}
