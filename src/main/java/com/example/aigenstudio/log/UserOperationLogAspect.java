package com.example.aigenstudio.log;

import com.example.aigenstudio.auth.CurrentUserContext;
import com.example.aigenstudio.domain.OperationLog;
import com.example.aigenstudio.domain.UserInfo;
import com.example.aigenstudio.service.OperationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class UserOperationLogAspect {

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(userOperationLog)")
    public Object recordOperation(ProceedingJoinPoint joinPoint, UserOperationLog userOperationLog) throws Throwable {
        Object response = null;
        Throwable throwable = null;
        try {
            response = joinPoint.proceed();
            return response;
        } catch (Throwable ex) {
            throwable = ex;
            throw ex;
        } finally {
            // 操作日志只记录用户主动触发的生成动作，任务状态和结果由 generation_task 承载。
            record(joinPoint.getArgs(), response, throwable, userOperationLog);
        }
    }

    private void record(Object[] args, Object response, Throwable throwable, UserOperationLog userOperationLog) {
        try {
            UserInfo userInfo = CurrentUserContext.getRequiredUser();
            OperationLog operationLog = new OperationLog();
            operationLog.setUserId(userInfo.getId());
            operationLog.setUsername(userInfo.getUsername());
            operationLog.setOperationType(userOperationLog.operationType().getCode());
            operationLog.setOperationName(userOperationLog.operationType().getDesc());
            operationLog.setRequestBody(toJson(sanitizeValue(args.length == 1 ? args[0] : args)));
            operationLog.setResponseBody(toJson(throwable == null
                    ? sanitizeValue(response)
                    : failureResponse(throwable)));
            operationLogService.record(operationLog);
        } catch (Exception ex) {
            // 操作日志不能反向影响生成主流程，记录失败只打 warn 供排查。
            log.warn("用户操作日志记录失败，reason={}", ex.getMessage());
        }
    }

    private Object sanitizeValue(Object value) {
        // 入参和出参写入操作日志前统一脱敏，防止参考图 base64 或大对象撑爆日志表。
        Object jsonValue = objectMapper.convertValue(value, Object.class);
        return sanitizeJsonValue(jsonValue);
    }

    private Map<String, Object> failureResponse(Throwable throwable) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", throwable.getMessage() == null ? "请求失败" : throwable.getMessage());
        return response;
    }

    private Object sanitizeJsonValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitizedMap = new LinkedHashMap<>();
            map.forEach((key, itemValue) -> sanitizedMap.put(String.valueOf(key), sanitizeJsonValue(itemValue)));
            return sanitizedMap;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::sanitizeJsonValue).toList();
        }
        if (value instanceof String text && text.startsWith("data:image/")) {
            return "data:image/*;base64,<length=" + text.length() + ">";
        }
        return value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
