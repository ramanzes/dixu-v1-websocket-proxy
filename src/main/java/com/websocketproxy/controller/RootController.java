package com.websocketproxy.controller;

import com.websocketproxy.repository.UsersSessionManager;
import com.websocketproxy.services.DeviceSessionManager;
import com.websocketproxy.services.HttpUtils;
import com.websocketproxy.services.logsandexceptions.MyLogger;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
//
//@Controller
//@Order(Ordered.LOWEST_PRECEDENCE)
//@RequestMapping(
//        value = {"/", "/{path:^(?!ws$).*}/**"}, // Чётко исключает /ws
//        method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
//                RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.OPTIONS}
//)
//public class RootController {
//
//    private final DeviceSessionManager deviceSessionManager;
//    private final HttpUtils httpUtils;
//
//    public RootController(DeviceSessionManager deviceSessionManager, HttpUtils httpUtils) {
//        this.deviceSessionManager = deviceSessionManager;
//        this.httpUtils = httpUtils;
//    }
//
//    @RequestMapping(
//            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
//                    RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.OPTIONS}
//    )
//    public void proxyRootRequest(HttpServletRequest request,
//                                 HttpServletResponse response,
//                                 HttpSession session) throws ServletException, IOException {
//
//        MyLogger.logServer("Request to RootController: " + request.getRequestURI());
//
//        if (request.getRequestURI().equals("/ws")) {
//            return; // Пропускаем WebSocket-handshake
//        }
//
//        String sessionId = session.getId();
//        MyLogger.logServer("User Session ID: " + sessionId);
//        UsersSessionManager usersSessionManager = httpUtils.getUsersSessionManager();
//
//        // Получаем deviceId из последнего запроса в сессии
//        String deviceId = null;
//        LinkedList<String> requestIds = new LinkedList<>(usersSessionManager.getRequestsForSession(sessionId));
//        if (!requestIds.isEmpty()) {
//            String lastRequestId = requestIds.getLast();
//            deviceId = httpUtils.extractDeviceId(lastRequestId);
//        }
//
//        // Если deviceId не найден, возвращаем 404
//        if (deviceId == null || deviceId.isEmpty()) {
//            response.sendError(HttpStatus.NOT_FOUND.value(), "Device not connected");
//            return;
//        }
//
//        // Формируем новый путь для проксирования
//        String originalPath = request.getRequestURI().substring(1); // Убираем ведущий "/"
//        String queryString = request.getQueryString();
//        String proxyPath = "/p/" + deviceId + "/" + originalPath +
//                (queryString != null ? "?" + queryString : "");
//
//        MyLogger.logServer("Forwarding to: " + proxyPath);
//
//
//        Collections.list(request.getHeaderNames()).forEach(name ->
//                MyLogger.logServer(name + ": " + request.getHeader(name)));
//        if (request.getCookies() != null) {
//            Arrays.stream(request.getCookies())
//                    .forEach(c -> MyLogger.logServer("Cookie: " + c.getName() + "=" + c.getValue()));
//        }
//
//        // Перенаправляем запрос через RequestDispatcher
//        RequestDispatcher dispatcher = request.getRequestDispatcher(proxyPath);
//        dispatcher.forward(request, response);
//    }
//}


@Controller
@Order(Ordered.LOWEST_PRECEDENCE)
@RequestMapping(
        value = {"/", "/{path:^(?!ws$).*}/**"}, // Чётко исключает /ws
        method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.OPTIONS}
)
public class RootController {
    private final DeviceSessionManager deviceSessionManager;
    private final HttpUtils httpUtils;

    public RootController(DeviceSessionManager deviceSessionManager, HttpUtils httpUtils) {
        this.deviceSessionManager = deviceSessionManager;
        this.httpUtils = httpUtils;
    }

    @RequestMapping(
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.OPTIONS}
    )
    public void proxyRootRequest(HttpServletRequest request,
                                 HttpServletResponse response,
                                 HttpSession session) throws ServletException, IOException {
        MyLogger.logServer("Request to RootController: " + request.getRequestURI());

        // Защита от обработки WebSocket соединений
        if (request.getRequestURI().equals("/ws")) {
            return; // Пропускаем WebSocket-handshake
        }

        String sessionId = session.getId();
        MyLogger.logServer("User Session ID: " + sessionId);

        UsersSessionManager usersSessionManager = httpUtils.getUsersSessionManager();

        // Получаем deviceId из последнего запроса в сессии
        String deviceId = null;
        LinkedList<String> requestIds = new LinkedList<>(usersSessionManager.getRequestsForSession(sessionId));

        if (!requestIds.isEmpty()) {
            String lastRequestId = requestIds.getLast();
            deviceId = httpUtils.extractDeviceId(lastRequestId);
        }

        // Если deviceId не найден, возвращаем 404
        if (deviceId == null || deviceId.isEmpty()) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "Device not connected");
            return;
        }

        // Формируем новый путь для проксирования
        String originalPath = request.getRequestURI().substring(1); // Убираем ведущий "/"
        String queryString = request.getQueryString();

        // Проверяем, не начинается ли оригинальный путь уже с /p/{deviceId}
        // чтобы избежать двойного префикса
        String proxyPath;
        if (originalPath.startsWith("p/" + deviceId)) {
            proxyPath = "/" + originalPath + (queryString != null ? "?" + queryString : "");
        } else {
            proxyPath = "/p/" + deviceId + "/" + originalPath +
                    (queryString != null ? "?" + queryString : "");
        }

        MyLogger.logServer("Forwarding to: " + proxyPath);

        // Логируем заголовки для отладки
        Collections.list(request.getHeaderNames()).forEach(name ->
                MyLogger.logServer(name + ": " + request.getHeader(name)));

        if (request.getCookies() != null) {
            Arrays.stream(request.getCookies())
                    .forEach(c -> MyLogger.logServer("Cookie: " + c.getName() + "=" + c.getValue()));
        }

        // Перенаправляем запрос через RequestDispatcher
        // Важно: все заголовки и куки сохраняются при forward автоматически
        RequestDispatcher dispatcher = request.getRequestDispatcher(proxyPath);
        dispatcher.forward(request, response);
    }
}