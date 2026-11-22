package com.bank.transaction.controller;

import com.bank.transaction.dto.ChatbotQueryRequest;
import com.bank.transaction.dto.ChatbotQueryResponse;
import com.bank.transaction.service.ChatbotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chatbot")
@CrossOrigin(origins = "*")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/query")
    public ResponseEntity<ChatbotQueryResponse> processChatQuery(@RequestBody ChatbotQueryRequest request) {
        ChatbotQueryResponse response = chatbotService.answerCustomerQuery(request);
        return ResponseEntity.ok(response);
    }
}
