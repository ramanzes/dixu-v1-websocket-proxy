package com.example.websocketproxy.repository;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
//класс для отслеживания какому именно пользователю по его id сессии принадлежат запросы
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


    public class UserSession {
        private final String id;

        //флаг показывающий были ли уже установлены изменения параметров сжатия для этой сессии
        //т.к. этот параметр будет влиять на логику приложения, нужно продумать когда и при каких обстоятельств этот флаг будет сброшен
        //для случая когда локальный сервер на устройстве например обновил свой бэкэнд, а сессия между прокси и пользователем всё та же...

        private boolean itIsHasChange = false;

        //поддерживает ли локальный сервер, такие как у пользовательского клиента, методы сжатия
        private boolean locServWithCompress = false;

        //какие именно методы сжатия поддерживаются локальным сервером на основе запроса с браузера пользвателя
        private Set<String> userMethodCompress = new HashSet<>();


        public UserSession(String id) {
            this.id = id;
        }

        private void setItIsHasChange(){
            this.itIsHasChange=true;
        }

        //когда это можно и нужно будет сбросить?
        private void resetItIsHasChange(){
            this.itIsHasChange=false;
        }

        public void setLocServWithCompress( Boolean locServWithCompress) {
            this.locServWithCompress = locServWithCompress;
            setItIsHasChange();
        }

        public void setUserMethodCompress(Set<String> userMethodCompress) {
            this.userMethodCompress = userMethodCompress;
            setItIsHasChange();
        }

        public String getId() {
            return id;
        }

        public Boolean getLocServWithCompress() {
            return locServWithCompress;
        }

        public Set<String> getUserMethodCompress() {
            return userMethodCompress;
        }
    }


}

