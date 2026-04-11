package com.example.springaialibabademo.service;

import org.springframework.stereotype.Service;

@Service
public class SimpleService {
    public String getString(String message) {
        if(message.isEmpty()){
            return "就这？";
        }else{
            return "你妈妈"+message;
        }
    }
}
