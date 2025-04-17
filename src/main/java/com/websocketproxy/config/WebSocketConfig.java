package com.websocketproxy.config;

import com.websocketproxy.services.logsandexceptions.exceptions.MyOtherExceptions;
import com.websocketproxy.websocket.WebSocketProxyHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.config.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer, WebSocketMessageBrokerConfigurer {
   private static int BUFFER_SIZE;
   private static int COMPRESSMINSIZE;
   private static boolean DEBUG;



//   private final ProxyWebSocketHandler proxyWebSocketHandler;
   private final WebSocketProxyHandler webSocketProxyHandler;

    public static int getBUFFER_SIZE() {
        return BUFFER_SIZE;
    }

    public static boolean isDEBUG() {
        return DEBUG;
    }

    public static int getCOMPRESSMINSIZE() {
        return COMPRESSMINSIZE;
    }


    public WebSocketConfig(WebSocketProxyHandler webSocketProxyHandler) {

//        this.proxyWebSocketHandler = proxyWebSocketHandler;
        this.webSocketProxyHandler = webSocketProxyHandler;

        Properties properties = new Properties();
        try (InputStream input = WebSocketConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new MyOtherExceptions("не заполнен файл конфигураций \"main.resources.application.properties\"");
            }
            properties.load(input);

            this.DEBUG = Boolean.parseBoolean(properties.getProperty("DEBUG"));
            this.BUFFER_SIZE = Integer.parseInt(properties.getProperty("BUFFER_SIZE"));
            this.COMPRESSMINSIZE = Integer.parseInt(properties.getProperty("COMPRESSMINSIZE"));

//            System.out.println("Application Name: " + appName);
//            System.out.println("Application Version: " + appVersion);
        } catch (IOException ex) {
            ex.printStackTrace();
        }





    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        // Увеличиваем размер сообщений, если это необходимо
        registry.setMessageSizeLimit(10 * 1024 * 1024); // Максимальный размер сообщения
        registry.setSendBufferSizeLimit(10 * 1024 * 1024); // Максимальный размер буфера отправки
      //  registry.setSendTimeLimit(60 * 1000); // Таймаут отправки
    //    registry.setSupportsPartialMessages(true); // Включаем поддержку фрагментированных сообщений
    }


    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Регистрируем ProxyWebSocketHandler на /proxy-ws
//        registry.addHandler(proxyWebSocketHandler, "/proxy-ws")
//                .setAllowedOrigins("*"); // Разрешаем запросы с любых доменов
        // Регистрируем WebSocketProxyHandler на /device-ws
        registry.addHandler(webSocketProxyHandler, "/ws")
                .setAllowedOrigins("*");  // Разрешаем запросы с любых доменов
//         .setAllowedOriginPatterns("http://localhost:*", "https://*.example.com", "172.16.42.1");
//                .setAllowedOriginPatterns("https://*.example.com", "http://localhost:*","0.0.0.0","127.0.0.1") // Используем allowedOriginPatterns
//                .setAllowedOrigins(null) // Полностью отключаем проверку источников
//                .withSockJS(); // Добавляем поддержку SockJS для обратной совместимости
    }

    // Bean для RestTemplate, если требуется HTTP-клиент
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }


}








//package com.example.websocketproxy.config;
//
//import com.example.websocketproxy.websocket.WebSocketProxyHandler;
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
