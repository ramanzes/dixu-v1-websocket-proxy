package com.example.websocketproxy.services.logsandexceptions;

import com.example.websocketproxy.config.WebSocketConfig;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.websocketproxy.services.MyWebsocketUtils.decompressData;

//import lombok.extern.slf4j.Slf4j;
//@Slf4j
public class MyLogger {
    // Объявление логгера
    //   private static final Logger log = LoggerFactory.getLogger(MyLogger.class);

//    public static void processMessage(String info, String message) {
//        // Использование логгера
//        //log.info("Received message: {}", message);
////        System.out.println(info+": "+message);
//        logServer(info + ": " + message);
//    }
//
//    public static void processMessageErr(String info, String message, Exception error) {
//        // Использование логгера
//        //log.info("Received message: {}", message);
////        System.err.println(info+": "+message+" ("+error+")");
//        logServer(info + ": " + message + " (" + error + ")");
//    }
//
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
        if (WebSocketConfig.isDEBUG()) {
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

    public static void logServByteToString(byte[] data) throws UnsupportedEncodingException {
        logServer("--------------------S-------------------------------",true);
        // Преобразуем байты в строку
        String decodedString = new String(data, StandardCharsets.UTF_8);
        //Метод возвращает строку, которая содержит только валидные данные для декодирования. т.к. мы нарезаем данные частями. то для логирования обрезка малой части невалидных данных не принципиальна
        String cleanedString = extractValidUrlEncodedData(decodedString);
        String result = URLDecoder.decode(cleanedString, "UTF-8");
        MyLogger.logServer(result,true);
        logServer("--------------------E-------------------------------",true);
    }

    public static void logSrvDecodeUnGzip(byte[] data) throws IOException {
        // Распаковываем данные
        byte[] decompressedData = decompressData(data);
        logServByteToString(decompressedData);
    }

    /**
     * Возвращает строку с валидными URL-encoded последовательностями.
     * Удаляет все неполные или некорректные последовательности.
     *
     * @param input Исходная строка
     * @return Строка с валидными данными для декодирования
     */
    public static String extractValidUrlEncodedData(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // Регулярное выражение для поиска валидных %XX последовательностей
        Pattern pattern = Pattern.compile("((?:%[0-9A-Fa-f]{2})+)|([^%]+)");
        Matcher matcher = pattern.matcher(input);

        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            // Если найдена валидная %XX последовательность или обычный текст
            if (matcher.group(1) != null || matcher.group(2) != null) {
                result.append(matcher.group());
            }
        }

        return result.toString();
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