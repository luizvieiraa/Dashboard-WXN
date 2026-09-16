package com.chatbot.whatsapp.controller;

import com.chatbot.whatsapp.dto.response.ConversationResponse;
import com.chatbot.whatsapp.dto.response.MessageResponse;
import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.service.ConversationService;
import com.chatbot.whatsapp.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de conversas e mensagens ja registradas.
 */
@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Conversas", description = "Consulta de conversas e mensagens")
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    public ConversationController(ConversationService conversationService, MessageService messageService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta uma conversa pelo id, incluindo suas mensagens")
    @ApiResponse(responseCode = "200", description = "Conversa encontrada")
    @ApiResponse(responseCode = "404", description = "Conversa nao encontrada")
    public ResponseEntity<ConversationResponse> getConversation(@PathVariable Long id) {
        Conversation conversation = conversationService.findById(id);
        List<MessageResponse> messages = messageService.listByConversation(id).stream()
                .map(MessageResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ConversationResponse.fromEntity(conversation, messages));
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Lista as mensagens de uma conversa, em ordem cronologica")
    @ApiResponse(responseCode = "200", description = "Lista de mensagens (pode ser vazia)")
    @ApiResponse(responseCode = "404", description = "Conversa nao encontrada")
    public ResponseEntity<List<MessageResponse>> getMessages(@PathVariable Long id) {
        // Garante 404 quando a conversa nao existe, em vez de devolver lista vazia silenciosamente.
        conversationService.findById(id);
        List<MessageResponse> messages = messageService.listByConversation(id).stream()
                .map(MessageResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(messages);
    }
}
