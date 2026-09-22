package com.chatbot.whatsapp.controller;

import com.chatbot.whatsapp.dto.response.ConversationResponse;
import com.chatbot.whatsapp.dto.response.MessageResponse;
import com.chatbot.whatsapp.dto.request.ClaimConversationRequest;
import com.chatbot.whatsapp.dto.request.HumanReplyRequest;
import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.service.ConversationService;
import com.chatbot.whatsapp.service.MessageService;
import com.chatbot.whatsapp.service.HumanAttendanceService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final HumanAttendanceService humanAttendanceService;

    public ConversationController(ConversationService conversationService,
                                  MessageService messageService,
                                  HumanAttendanceService humanAttendanceService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.humanAttendanceService = humanAttendanceService;
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

    @PostMapping("/{id}/human/claim")
    @Operation(summary = "Atribui uma conversa da fila humana a um atendente")
    public ResponseEntity<ConversationResponse> claim(
            @PathVariable Long id,
            @Valid @RequestBody ClaimConversationRequest request) {
        return ResponseEntity.ok(toResponse(humanAttendanceService.claim(id, request.attendant())));
    }

    @PostMapping("/{id}/human/reply")
    @Operation(summary = "Envia uma resposta em nome do atendente responsavel")
    public ResponseEntity<ConversationResponse> reply(
            @PathVariable Long id,
            @Valid @RequestBody HumanReplyRequest request) {
        return ResponseEntity.ok(toResponse(humanAttendanceService.reply(id, request.message())));
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Encerra uma conversa")
    public ResponseEntity<ConversationResponse> close(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(humanAttendanceService.close(id)));
    }

    private ConversationResponse toResponse(Conversation conversation) {
        List<MessageResponse> messages = messageService.listByConversation(conversation.getId()).stream()
                .map(MessageResponse::fromEntity)
                .toList();
        return ConversationResponse.fromEntity(conversation, messages);
    }
}
