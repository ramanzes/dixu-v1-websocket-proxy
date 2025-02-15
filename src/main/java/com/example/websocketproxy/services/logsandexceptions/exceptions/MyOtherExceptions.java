package com.example.websocketproxy.services.logsandexceptions.exceptions;

public class MyOtherExceptions extends RuntimeException{
    public MyOtherExceptions(String message, Throwable cause){
        super(message, cause);
    }
}
