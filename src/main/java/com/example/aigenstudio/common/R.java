package com.example.aigenstudio.common;

import lombok.Data;

@Data
public class R<T> {

    // 业务状态码，成功固定为 200。
    private int code;
    // 前端可展示的响应消息。
    private String msg;
    // 响应数据体，失败时可为空。
    private T data;

    public static <T> R<T> ok(T data) {
        R<T> response = new R<>();
        response.setCode(200);
        response.setMsg("success");
        response.setData(data);
        return response;
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> response = new R<>();
        response.setCode(code);
        response.setMsg(message);
        return response;
    }

}
