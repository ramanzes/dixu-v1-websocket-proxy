package com.example.websocketproxy.controller;

import com.example.websocketproxy.config.WebSocketConfig;
import com.example.websocketproxy.repository.UsersSessionManager;
import com.example.websocketproxy.services.*;
import com.example.websocketproxy.services.HttpRequest;
import com.example.websocketproxy.websocket.WebSocketProxyHandler;
import com.example.websocketproxy.services.logsandexceptions.MyLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import java.util.Map;

import static com.example.websocketproxy.config.WebSocketConfig.*;



@Controller
@RequestMapping(
        value = "/p"
)
@SessionAttributes("userSessionManager")  // Связываем сессию с пользователем
public class ProxyController {



    private final DeviceSessionManager deviceSessionManager;
    private final WebSocketProxyHandler webSocketProxyHandler;
    private final MyWebsocketUtils myWebsocketUtils;
    private final HttpRequest httpRequest;
    private final HttpResponse httpResponse;
    private final HttpUtils httpUtils;



    public ProxyController(DeviceSessionManager deviceSessionManager, WebSocketProxyHandler webSocketProxyHandler, HttpRequest httpRequest, HttpResponse httpResponse) {
        this.deviceSessionManager = deviceSessionManager;
        this.webSocketProxyHandler = webSocketProxyHandler;
        this.myWebsocketUtils = new MyWebsocketUtils(this.deviceSessionManager);
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
        this.httpUtils = new HttpUtils(this.deviceSessionManager);
    }



@RequestMapping(
        value = "/{deviceId}/**",
        method = {RequestMethod.GET}
)
public ResponseEntity<byte[]> proxyRequest(@PathVariable String deviceId,
                                           HttpServletRequest request, HttpSession session)  // Получаем HTTP-сессию пользователя
 {
    WebSocketSession deviceSession = deviceSessionManager.getSessionForThisDevice(deviceId);

    if (deviceSession == null || !deviceSession.isOpen()) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Device is not connected".getBytes(StandardCharsets.UTF_8));
    }

    try {

        // Получаем HTTP-сессию пользователя
        String sessionId = session.getId();
        MyLogger.logServer("User Session ID: " + sessionId);

        // Формируем HTTP-запрос и получаем его вместе с requestId, передаём и sessionId в строителя заголовка
//   и там же связываем запрос с клиентской сессией
        final Map<String, String> httpRequestMap = httpUtils.buildHttpRequest(request, deviceId, sessionId);

        if (httpRequestMap.isEmpty()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to build HTTP request".getBytes(StandardCharsets.UTF_8));
        }

        // Извлекаем requestId и сам запрос из мапы
       // !! проследить где мы освобождаем память от этого запроса после использования... т.е. получения полностью ответа
        String requestId = httpRequestMap.keySet().iterator().next();

        UsersSessionManager usersSessionManager = httpUtils.getUsersSessionManager();
        //связываем запрос с клиентской сессией
        //!! также нужно будет освободиться от этого запроса после ответа
        usersSessionManager.addRequestToSession(sessionId,requestId);

        //если это вообще первый запрос от клиента, то нужно понять какие методы сжатия поддерживает его браузер


        String httpRequest = httpRequestMap.get(requestId);
        MyLogger.logServer("requestPath: [ " + httpRequest.split("\n")[0] + "]");
        MyLogger.logServer("httpRequest: ["+httpRequest+"]");


        // Добавляем sessionId в WebSocket-сообщение !!!! теперь научиться это парсить на стороне клиента
//        String wrappedRequest = "SESSION-ID: " + sessionId + "\n" + httpRequest;

        // Отправляем запрос устройству через WebSocket
//        myWebsocketUtils.sendMessage(deviceSession, new TextMessage(wrappedRequest));

        // Отправляем запрос устройству через WebSocket
        myWebsocketUtils.sendMessage(deviceSession, new TextMessage(httpRequest));

        // Вызываем метод для обработки ответа устройства
        return (ResponseEntity<byte[]>) httpResponse.processDeviceResponse(requestId,webSocketProxyHandler,myWebsocketUtils);

    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Error occurred: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
    }
}

//обработка POST запросов для типа контента без multipart
@PostMapping(
        value = "/{deviceId}/**",
        consumes = {
            "application/json",
            "application/xml",
            "text/plain",
            "application/x-www-form-urlencoded",
            "application/octet-stream"
        }
)
public ResponseEntity<byte[]> proxyPostSimpleRequest(@PathVariable String deviceId,
                                           HttpServletRequest request, HttpSession session) {
    WebSocketSession deviceSession = deviceSessionManager.getSessionForThisDevice(deviceId);

    if (deviceSession == null || !deviceSession.isOpen()) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Device is not connected".getBytes(StandardCharsets.UTF_8));
    }

    try {

        // Получаем HTTP-сессию пользователя
        // это сессия между прокси и браузером клиента. а есть ещё сессия между локальным сервером и устройством которую тоже нужно проксировать
        String sessionId = session.getId();
        MyLogger.logServer("User Session ID: " + sessionId);
        // Формируем HTTP-запрос и получаем его вместе с requestId
        final Map<String, String> httpRequestMap = httpUtils.buildHttpRequest(request, deviceId, sessionId);
        // Извлекаем requestId и сам запрос из мапы
        final String requestId = httpRequestMap.keySet().iterator().next();
        String httpRequest = httpRequestMap.get(requestId);

        MyLogger.logServer("requestPath: [ " + httpRequest.split("\n")[0] + "]");
        MyLogger.logServer("httpRequest: ["+httpRequest+"]");
        // Отправляем запрос устройству через WebSocket / этот запрос создаст хранилище в onMessage для получения тела post по этому idRequest
        myWebsocketUtils.sendMessage(deviceSession, new TextMessage(httpRequest));
        //инициируем переменную для приёма потока данных от клиента из его запроса, из тела post
        InputStream inputStream = request.getInputStream();
        int initialBufferSize = getBUFFER_SIZE();

        // Читаем первые байты, чтобы определить стратегию
        byte[] initialBuffer = new byte[initialBufferSize];
        // и сохраняем её в initialBuffer
        int bytesRead = inputStream.read(initialBuffer);

        if (bytesRead == -1) {
            MyLogger.logServer("POST-запрос пустой", true);
            throw new MyLogger.CustomException("пустое тело запроса post");
//                    return;
        }
//        boolean shouldCompress = myWebsocketUtils.shouldCompress(request.getContentType(), bytesRead, request.getRequestURI());

        byte[] requestBody;
        if(bytesRead < getCOMPRESSMINSIZE()){
            MyLogger.logServer("Тело POST имеет маленький размер, отправляем сразу без сжатия в одном бинарном запросе, размер" + bytesRead + " байт");
            // Отправляем только прочитанные данные
            byte[] dataToSend = Arrays.copyOf(initialBuffer, bytesRead);
            MyLogger.logServByteToString(dataToSend);
            myWebsocketUtils.sendBinaryMessage(deviceSession, requestId, dataToSend, true, false);
        //иначе если данные большие и должны быть сжаты
//!!!!!! здесь нужно проверять если данные уже сжаты в заголовках взять инфу, то соответственно сжимать их уже не нужно это раз. но флаг gzip должен быть true/ А может ли клиент браузера вообще сам сжимать данные? при отправке данных пост
        } else if (myWebsocketUtils.shouldCompress(request.getContentType(), bytesRead, request.getRequestURI()))  {
            //по идее сюда мы попадаем только в том случае если пользовательский браузер не поддерживает сжатие
            // или клиентски локальный сервер не поддерживает сжатие
            // тогда в заголовках это указано и пользователь отправляет на прокси не сжатые большие данные, мы их сжимаем за него и проксируем на клиента - локальный севрер, и расжимаем по своей логике на клиенте и передаём на его локальный сервер расжатые данные


            //!!! но если данные были сжаты браузером то наша логика на клиенте должна и это учитывать! т.е. мы должны проксировать сжатые данные как сжатые данные по стандарной логике... на клиентский локальный сервер

            requestBody = MyWebsocketUtils.compressData(initialBuffer);
            int compressDataLength = requestBody.length;
            MyLogger.logServer("Тело POST, размер до сжатия: " + bytesRead + " байт");
            MyLogger.logServer("Тело POST сжато (GZIP), размер после: " + compressDataLength + " байт");
//            MyLogger.logServByteToString(initialBuffer);
            // значит отправлено не всё и скорее всего остались в потоке ещё данные
            if (bytesRead == getBUFFER_SIZE()) {
                //отправляем первую часть уже прочитанных данных
                myWebsocketUtils.sendBinaryMessage(deviceSession, requestId, requestBody, false, true);
                //отправляем остальные данные частями, начиная от конца уже отправленной первой части.
//                myWebsocketUtils.sendChunkInputStream(deviceSession, requestId, inputStream, true, compressDataLength);
//                это делает движок сам. т.е. читает данные следующие от прочитанных из потока
                myWebsocketUtils.sendChunkInputStream(deviceSession, requestId, inputStream, true);
            } else if (bytesRead < getBUFFER_SIZE()){
                myWebsocketUtils.sendBinaryMessage(deviceSession, requestId, requestBody, true, true);
            }
        } else{
            //это могут быть большие данные уже сжатые или не требующие сжатия по тем или иным причинам
            MyLogger.logServer("Тело POST отправляется потоком без сжатия");

            if (bytesRead < getBUFFER_SIZE()) {
                //отправляем уже считанную часть в самом начале
                myWebsocketUtils.sendBinaryMessage(deviceSession, requestId, initialBuffer, true, false);
            } else if (bytesRead == getBUFFER_SIZE()) {
                //отправляем уже считанную часть в самом начале
                myWebsocketUtils.sendBinaryMessage(deviceSession, requestId, initialBuffer, false, false);
                // отправляем остальные части т.е. из inputStream уже считана первая часть продолжим от туда
//                myWebsocketUtils.sendChunkInputStream(deviceSession, requestId, inputStream, false, bytesRead);
// движок сам должен давать только следующие данные из потока
                myWebsocketUtils.sendChunkInputStream(deviceSession, requestId, inputStream, false);
                // Вызываем новый метод для обработки ответа устройства
            }
        }

        inputStream.close();

        // Вызываем новый метод для обработки ответа устройства
//        return (ResponseEntity<byte[]>) httpResponse.processDeviceResponse(requestId,webSocketProxyHandler,myWebsocketUtils);

        //{!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!}

        // Когда получаем ответ от устройства
        ResponseEntity response = (ResponseEntity<byte[]>) httpResponse.processDeviceResponse(requestId, webSocketProxyHandler, myWebsocketUtils);

//        response = processDeviceResponse(response);

        return response;

    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Error occurred: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
    }
}



    /**
     * Обрабатывает ответ от локального сервера и при необходимости обновляет URL в браузере
     * сохраняя базовый путь прокси (/p/device-XXX) и добавляя к нему новый путь из заголовка
     */
    public ResponseEntity<?> processDeviceResponse(ResponseEntity<?> response) {
        // Сначала выведем все заголовки для диагностики
        debugHeaders(response.getHeaders());

        // Проверяем наличие заголовка X-Request-URL и получаем его значение
        String newUrl = extractRequestUrl(response.getHeaders());

        // Если найден заголовок X-Request-URL и ответ содержит HTML
        if (newUrl != null &&
                response.getHeaders().getContentType() != null &&
                response.getHeaders().getContentType().includes(MediaType.TEXT_HTML)) {

            byte[] responseBody = (byte[]) response.getBody();
            if (responseBody == null) {
                return response; // Если тело ответа пустое, возвращаем как есть
            }

            // Преобразуем тело ответа в строку
            String html = new String(responseBody, StandardCharsets.UTF_8);

            // Получаем относительный путь из полного URL
            String relPath = getPartUrl(newUrl);

            // Формируем JavaScript для обновления URL с сохранением базового пути прокси
            String script = createUrlUpdateScript(relPath);

            // Добавляем скрипт перед закрывающим тегом </body>
            if (html.contains("</body>")) {
                html = html.replace("</body>", script + "</body>");
            } else {
                // Если тег </body> не найден, добавляем скрипт в конец документа
                html = html + script;
            }

            // Возвращаем модифицированный ответ
            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body(html.getBytes(StandardCharsets.UTF_8));
        }

        // Если не требуется изменение URL или формат не HTML, возвращаем исходный ответ
        return response;
    }

    /**
     * Извлекает URL из заголовков ответа
     */
    private String extractRequestUrl(HttpHeaders headers) {
        String newUrl = null;

        // Сначала пробуем стандартное имя заголовка
        if (headers.containsKey("X-Request-URL")) {
            newUrl = headers.getFirst("X-Request-URL");
        } else {
            // Если не найдено, ищем по частичному совпадению (нестрогий поиск)
            for (String headerName : headers.keySet()) {
                if (headerName.toLowerCase().contains("x-request-url")) {
                    newUrl = headers.getFirst(headerName);
                    break;
                }
            }
        }

        // Обрабатываем случай, когда значение начинается с ": "
        if (newUrl != null && newUrl.startsWith(": ")) {
            newUrl = newUrl.substring(2);
        }

        return newUrl;
    }

    /**
     * Создает JavaScript для обновления URL в браузере
     */
    private String createUrlUpdateScript(String relPath) {
        return "<script>\n" +
                "  // Получаем текущий URL\n" +
                "  let currentUrl = window.location.href;\n" +
                "  // Удаляем завершающий слеш, если есть\n" +
                "  if (currentUrl.endsWith('/') && currentUrl.length > 1) {\n" +
                "    currentUrl = currentUrl.slice(0, -1);\n" +
                "  }\n" +
                "  // Находим базовый URL прокси\n" +
                "  let baseUrl = currentUrl;\n" +
                "  // Формат URL: https://localhost:8443/p/device-XXXX\n" +
                "  if (baseUrl.indexOf('/p/') > -1) {\n" +
                "    // Разбиваем URL по сегменту '/p/'\n" +
                "    let parts = baseUrl.split('/p/');\n" +
                "    if (parts.length > 1) {\n" +
                "      // Берем первый сегмент после '/p/' (device-XXXX)\n" +
                "      let deviceSegment = parts[1].split('/')[0];\n" +
                "      // Собираем базовый URL: протокол + хост + '/p/' + deviceId\n" +
                "      baseUrl = parts[0] + '/p/' + deviceSegment;\n" +
                "    }\n" +
                "  }\n" +
                "  // Убеждаемся, что путь начинается с '/'\n" +
                "  let newPath = '" + relPath + "';\n" +
                "  if (!newPath.startsWith('/')) {\n" +
                "    newPath = '/' + newPath;\n" +
                "  }\n" +
                "  // Добавляем новый путь к базовому URL\n" +
                "  const newUrl = baseUrl + newPath;\n" +
                "  // Обновляем URL в браузере без перезагрузки страницы\n" +
                "  window.history.pushState({}, '', newUrl);\n" +
                "  console.log('URL обновлен на: ' + newUrl);\n" +
                "</script>";
    }

    /**
     * Извлекает относительный путь из полного URL
     */
    public String getPartUrl(String fullUrl) {
        String relativePath = "";
        if (fullUrl != null) {
            try {
                URL url = new URL(fullUrl);
                relativePath = url.getPath(); // Получаем только путь без домена

                // Добавляем query параметры, если они есть
                if (url.getQuery() != null && !url.getQuery().isEmpty()) {
                    relativePath += "?" + url.getQuery();
                }
            } catch (MalformedURLException e) {
                // Обработка ошибки
                System.err.println("Неверный формат URL: " + e.getMessage());
            }
        }
        return relativePath;
    }

    /**
     * Выводит все заголовки для диагностики
     */
    private void debugHeaders(HttpHeaders headers) {
        System.out.println("---- DEBUG: Response Headers ----");
        for (String headerName : headers.keySet()) {
            System.out.println(headerName + " = " + headers.get(headerName));
        }
        System.out.println("--------------------------------");
    }





//контроллер обрабатывает Post запросы multipart

    @PostMapping(
            value = "/{deviceId}/**",
            consumes = {
                    "multipart/form-data"
            }
    )
    public ResponseEntity<byte[]> proxyMultipartPostRequest(@PathVariable String deviceId,
                                                            MultipartHttpServletRequest request, HttpSession session) {
        WebSocketSession deviceSession = deviceSessionManager.getSessionForThisDevice(deviceId);
        if (deviceSession == null || !deviceSession.isOpen()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Device is not connected".getBytes(StandardCharsets.UTF_8));
        }

        try {

            // Получаем HTTP-сессию пользователя
            String sessionId = session.getId();
            MyLogger.logServer("User Session ID: " + sessionId);
            // Формируем HTTP-запрос и получаем его вместе с requestId
            Map<String, String> httpRequestMap = httpUtils.buildHttpRequest(request, deviceId, sessionId);
            // Извлекаем requestId и сам запрос из мапы
            String requestId = httpRequestMap.keySet().iterator().next();
            String httpRequest = httpRequestMap.get(requestId);

            MyLogger.logServer("requestPath: [ " + httpRequest.split("\n")[0] + "]");
            MyLogger.logServer("httpRequest: [" + httpRequest + "]");

            // Отправляем запрос устройству через WebSocket
            myWebsocketUtils.sendMessage(deviceSession, new TextMessage(httpRequest));

            // Получаем boundary из заголовка Content-Type
            String contentType = request.getContentType();
            String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);

            // Обрабатываем multipart-данные
            for (Map.Entry<String, MultipartFile> entry : request.getFileMap().entrySet()) {
                MultipartFile file = entry.getValue();
                String fileName = file.getOriginalFilename();
                MyLogger.logServer("Обрабатываем файл: " + fileName);

                // Отправляем файл потоком
               myWebsocketUtils.sendMultipartFormDataStream(deviceSession, requestId, boundary, entry.getKey(), file);
            }

            // Обрабатываем текстовые данные формы (если есть)
            Map<String, String[]> formData = request.getParameterMap();
            for (Map.Entry<String, String[]> entry : formData.entrySet()) {
                String key = entry.getKey();
                String[] values = entry.getValue();
                MyLogger.logServer("Текстовые данные формы: " + key + " = " + String.join(", ", values));
            }

            // Вызываем метод для обработки ответа устройства
            return (ResponseEntity<byte[]>) httpResponse.processDeviceResponse(requestId, webSocketProxyHandler, myWebsocketUtils);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error occurred: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }






}








