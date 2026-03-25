package com.example.springaialibabademo.controller;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping("/simple")
    public ChatResponse simpleChat(@RequestParam(defaultValue = "请用一句话介绍 Spring AI Alibaba。") String message) {
        return new ChatResponse(message, generateReply(message), OffsetDateTime.now());
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return new ChatResponse(request.message(), generateReply(request.message()), OffsetDateTime.now());
    }

    @GetMapping("/health")
    public DemoStatusResponse health() {
        return new DemoStatusResponse("ok", "Spring AI Alibaba demo is ready.");
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    private String generateReply(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }

        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    public record ChatRequest(@NotBlank(message = "message 不能为空") String message) {
    }

    public record ChatResponse(String question, String answer, OffsetDateTime timestamp) {
    }

    public record DemoStatusResponse(String status, String description) {
    }

    public record ErrorResponse(String message) {
    }
}
