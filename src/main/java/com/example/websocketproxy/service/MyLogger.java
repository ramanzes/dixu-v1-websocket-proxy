package com.example.websocketproxy.service;


import java.util.concurrent.TimeoutException;

//import lombok.extern.slf4j.Slf4j;
//@Slf4j
public class MyLogger {
    // Объявление логгера
 //   private static final Logger log = LoggerFactory.getLogger(MyLogger.class);

    public static void processMessage(String info, String message) {
        // Использование логгера
        //log.info("Received message: {}", message);
        System.out.println(info+": "+message);
    }

    public static void processMessageErr(String info, String message, Exception error) {
        // Использование логгера
        //log.info("Received message: {}", message);
        System.err.println(info+": "+message+" ("+error+")");
    }


}
