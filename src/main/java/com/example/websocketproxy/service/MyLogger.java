package com.example.websocketproxy.service;

import com.example.websocketproxy.config.WebSocketConfig;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeoutException;

//import lombok.extern.slf4j.Slf4j;
//@Slf4j
public class MyLogger {
    // Объявление логгера
    //   private static final Logger log = LoggerFactory.getLogger(MyLogger.class);

    public static void processMessage(String info, String message) {
        // Использование логгера
        //log.info("Received message: {}", message);
//        System.out.println(info+": "+message);
        logServer(info + ": " + message);
    }

    public static void processMessageErr(String info, String message, Exception error) {
        // Использование логгера
        //log.info("Received message: {}", message);
//        System.err.println(info+": "+message+" ("+error+")");
        logServer(info + ": " + message + " (" + error + ")");
    }

    public static String getTimeNow() {
        // Получаем текущее время в человеческом формате
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedTime = now.format(formatter);
        // Вычисляем время выполнения
        long duration = System.nanoTime(); // Время в наносекундах
        double durationInMillis = duration / 1_000_000.0; // Преобразуем в миллисекунды
        return formattedTime + "(" + durationInMillis + "ms)";
    }


    //логи для дебага
    //неважно что ставит разраб true или false так как если он указывает этот параметр, то будет действовать условие из константы. благодаря перезагрузки метода
    public static void logServer(String logs, boolean debug) {
        if (WebSocketConfig.DEBUG) {
            // Получаем стек вызовов
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            // Получаем элемент стека, который соответствует методу printWithContext
            StackTraceElement caller = stackTrace[2]; // 0 - это getStackTrace, 1 - printWithContext, 2 - вызывающий метод
            // Получаем информацию о классе и методе
            String className = caller.getClassName();
            String methodName = caller.getMethodName();
            String result = "[" + className + "." + methodName + "] " + logs;
            // Выводим сообщение с контекстом
            System.out.println("websocket-server Logs(" + getTimeNow() + "): " + result);
        }
    }


    //для логов в продакшен
    public static void logServer(String logs) {
        // Получаем стек вызовов
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // Получаем элемент стека, который соответствует методу printWithContext
        StackTraceElement caller = stackTrace[2]; // 0 - это getStackTrace, 1 - printWithContext, 2 - вызывающий метод
        // Получаем информацию о классе и методе
        String className = caller.getClassName();
        String methodName = caller.getMethodName();
        String result = "[" + className + "." + methodName + "] " + logs;
        // Выводим сообщение с контекстом
        System.out.println("websocket-server Logs(" + getTimeNow() + "): " + result);
    }


    public static class CustomException extends Exception {
        private String errorCode;

        public CustomException(String message) {
            super(message);
        }

        public CustomException(String message, String errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

}