package com.websocketproxy.repository;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
//класс управления пользовательскими http сессиями устанавливаемых с прокси сервером
//класс для отслеживания какому именно пользователю по его id-сессии(HTTP) принадлежат запросы
public class UsersSessionManager {
    private Map<String, Set<String>> sessionMap; // Хранит sessionId и соответствующие requestId
    private Map<String, String> requestToSessionMap; // Хранит соответствие requestId и sessionId

    private static UsersSessionManager instance;

    // Статический метод для получения экземпляра класса
    public static UsersSessionManager getInstance() {
        if (instance == null) {
            instance = new UsersSessionManager();
        }
        return instance;
    }

    //по idSession храним данные в объекте по этой сессии
    private Map<String, UserSession> usersSessionMap = new HashMap<>();

    private Map<String, UserSession> getUsersSessionMap() {
        return usersSessionMap;
    }
    public UserSession getUserSession(String sessionId){
        return getUsersSessionMap().get(sessionId);
    }


    //конструктор вызывается один раз
    private UsersSessionManager() {
        sessionMap = new HashMap<>();
        requestToSessionMap = new HashMap<>();
    }

    // Добавление requestId к сессии
    public void addRequestToSession(String idSession, String requestId) {
        sessionMap.computeIfAbsent(idSession, k -> new HashSet<>()).add(requestId);
        requestToSessionMap.put(requestId, idSession); // Сохраняем соответствие requestId и sessionId
        usersSessionMap.put(idSession,new UserSession(idSession));
    }

    // Удаление requestId из сессии
    public void removeRequestFromSession(String idSession, String requestId) {
        Set<String> requestIds = sessionMap.get(idSession);
        if (requestIds != null) {
            requestIds.remove(requestId);
            requestToSessionMap.remove(requestId); // Удаляем соответствие requestId и sessionId
            // Если множество пустое, можно удалить сессию
            if (requestIds.isEmpty()) {
                sessionMap.remove(idSession);
            }
        }
    }

    // Получение всех requestId для данной сессии
    public Set<String> getRequestsForSession(String idSession) {
        return sessionMap.getOrDefault(idSession, new HashSet<>());
    }

    // Проверка, содержится ли requestId в сессии
    public boolean containsRequestInSession(String idSession, String requestId) {
        Set<String> requestIds = sessionMap.get(idSession);
        return requestIds != null && requestIds.contains(requestId);
    }

    // Получение sessionId по requestId
    public String getSessionIdByRequestId(String requestId) {
        return requestToSessionMap.get(requestId);
    }





}

