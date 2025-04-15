package com.example.websocketproxy.repository;

import java.util.HashSet;
import java.util.Set;

// используется только в родительском классе
public class UserSession {
    private final String id;

    //флаг показывающий были ли уже установлены изменения параметров сжатия для этой сессии
    //т.к. этот параметр будет влиять на логику приложения, нужно продумать когда и при каких обстоятельств этот флаг будет сброшен
    //для случая когда локальный сервер на устройстве например обновил свой бэкэнд, а сессия между прокси и пользователем всё та же...

    private boolean itIsHasChange = false;

//    //поддерживает ли локальный сервер, такие как у пользовательского клиента, методы сжатия
// Я ДУМАЮ ЭТОТ ПАРАМЕТР НЕ КАСАЕТСЯ ПРОСТРАНСТВА ПОЛЬЗОВАТЕЛЯ. ОН ОТНОСИТСЯ К ДЕВАЙСУ
//    private boolean locServWithCompress = false;

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

//    public void setLocServWithCompress( Boolean locServWithCompress) {
//        this.locServWithCompress = locServWithCompress;
//        setItIsHasChange();
//    }
//
    public void setUserMethodCompress(Set<String> userMethodCompress) {
        this.userMethodCompress = userMethodCompress;
        setItIsHasChange();
    }

    public String getId() {
        return id;
    }

//    public Boolean getLocServWithCompress() {
//        return locServWithCompress;
//    }

    public Set<String> getUserMethodCompress() {
        return userMethodCompress;
    }
}
