package com.smartfinance.ai.controller;

import com.smartfinance.ai.dto.ChatRequest;
import com.smartfinance.ai.dto.ChatResponse;
import com.smartfinance.ai.service.ChatService;
import com.smartfinance.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("Authorization") String authHeader) {

        return ApiResponse.ok(chatService.chat(
                request.message(), request.conversationId(), userId, authHeader));
    }

    @DeleteMapping("/chat/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearChat(
            @PathVariable("conversationId") String conversationId,
            @RequestHeader("X-User-Id") UUID userId) {

        chatService.clearConversation(conversationId, userId);
    }
}
