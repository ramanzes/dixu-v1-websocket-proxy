package com.example.websocketproxy.services;

import com.example.websocketproxy.services.logsandexceptions.MyLogger;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class HttpUtils {

    public static int getHeadersSize(HttpHeaders headers) {
        int headersSize = 0;

        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            // Размер имени заголовка
            headersSize += entry.getKey().length();
            // Размер значений заголовка
            headersSize += String.join(", ", entry.getValue()).length();
            // Добавляем 4 байта для ": " и "\r\n"
            headersSize += 4;
        }


        MyLogger.logServer("headerSize1: "+String.valueOf(headersSize));

        headersSize=0;

            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                // Размер имени заголовка
                headersSize += entry.getKey().getBytes(StandardCharsets.UTF_8).length;
                // Размер значений заголовка
                for (String value : entry.getValue()) {
                    headersSize += value.getBytes(StandardCharsets.UTF_8).length;
                }
                // Добавляем 4 байта для ": " и "\r\n"
                headersSize += 4;
            }

        MyLogger.logServer("HeadersSize2: "+String.valueOf(headersSize));
        return headersSize;
    }
}