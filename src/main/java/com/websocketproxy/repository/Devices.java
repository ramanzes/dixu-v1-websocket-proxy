package com.websocketproxy.repository;

import org.springframework.web.socket.WebSocketSession;

import java.util.Set;

public class Devices {
        final private String id;
        final private WebSocketSession session;
        //поддерживается ли сжатие на локальном сервере. устанавливается при определении методов сжатия в setMethodCompress
        private Boolean localservWithCompress = false;
        private Set <String> methodCompress = null;

        public Devices(String id, WebSocketSession session) {
            this.id = id;
            this.session = session;
        }

        private void setLocalservWithCompress(Boolean localservWithCompress) {
            this.localservWithCompress = localservWithCompress;
        }

        public void setMethodCompress(Set <String> methodCompress) {
            //если методы были определены уже для устройства ранее в одном из запросов, то определенные методы не меняются для этой сессии
            if (!methodCompress.isEmpty()) {
                this.methodCompress = methodCompress;
                setLocalservWithCompress(!methodCompress.isEmpty());
            }
        }

        public String getId() {
            return id;
        }

        public WebSocketSession getSession() {
            return session;
        }

        public Boolean getLocalservWithCompress() {
            return localservWithCompress;
        }

        public Set <String> getMethodCompress() {
            return methodCompress;
        }

        public Devices(String id, WebSocketSession session, Boolean localservWithCompress, Set <String> methodCompress) {
            this.id = id;
            this.session = session;
            this.localservWithCompress = localservWithCompress;
            this.methodCompress = methodCompress;
        }



    }

