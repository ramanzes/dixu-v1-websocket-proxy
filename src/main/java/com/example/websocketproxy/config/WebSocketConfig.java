package com.example.websocketproxy.config;

import com.example.websocketproxy.WebSocketProxyHandler;
import com.example.websocketproxy.websocket.ProxyWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer, WebSocketMessageBrokerConfigurer {

    private final ProxyWebSocketHandler proxyWebSocketHandler;
    private final WebSocketProxyHandler webSocketProxyHandler;

    public WebSocketConfig(ProxyWebSocketHandler proxyWebSocketHandler, WebSocketProxyHandler webSocketProxyHandler) {
        this.proxyWebSocketHandler = proxyWebSocketHandler;
        this.webSocketProxyHandler = webSocketProxyHandler;
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        // Увеличиваем размер сообщений, если это необходимо
        registry.setMessageSizeLimit(10 * 1024 * 1024); // Максимальный размер сообщения
        registry.setSendBufferSizeLimit(10 * 1024 * 1024); // Максимальный размер буфера отправки
    }


    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Регистрируем ProxyWebSocketHandler на /proxy-ws
        registry.addHandler(proxyWebSocketHandler, "/proxy-ws")
                .setAllowedOrigins("*"); // Разрешаем запросы с любых доменов

        // Регистрируем WebSocketProxyHandler на /device-ws
        registry.addHandler(webSocketProxyHandler, "/ws")
                .setAllowedOrigins("*"); // Разрешаем запросы с любых доменов
    }

    // Bean для RestTemplate, если требуется HTTP-клиент
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}








//package com.example.websocketproxy.config;
//
//import com.example.websocketproxy.WebSocketProxyHandler;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.client.RestTemplate;
//import org.springframework.web.socket.config.annotation.*;
//import com.example.websocketproxy.websocket.ProxyWebSocketHandler;
//
//@Configuration
//@EnableWebSocket
//public class WebSocketConfig implements WebSocketConfigurer, WebSocketMessageBrokerConfigurer {
//
//    private final ProxyWebSocketHandler proxyWebSocketHandler;
//    private final WebSocketProxyHandler webSocketProxyHandler;
//
//    public WebSocketConfig(ProxyWebSocketHandler proxyWebSocketHandler, WebSocketProxyHandler webSocketProxyHandler) {
//        this.proxyWebSocketHandler = proxyWebSocketHandler;
//        this.webSocketProxyHandler = webSocketProxyHandler;
//    }
//    @Override
//    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
//        registry.setMessageSizeLimit(65536); // Увеличьте лимит
//    }
//    @Override
//    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
//        registry.addHandler(proxyWebSocketHandler, "/ws")
//                .setAllowedOrigins("*"); // Разрешить запросы с любых доменов
//        registry.addHandler(webSocketProxyHandler, "/ws")
//                .setAllowedOrigins("*"); // Разрешить запросы с любых доменов
//    }
//
////Добавьте этот метод
////Метод restTemplate в контексте Spring-приложений используется для выполнения HTTP-запросов к RESTful веб-сервисам. Он позволяет отправлять запросы и получать ответы от внешних API
////    @Bean
////    public RestTemplate restTemplate() {
////        return new RestTemplate();
////    }
//}
