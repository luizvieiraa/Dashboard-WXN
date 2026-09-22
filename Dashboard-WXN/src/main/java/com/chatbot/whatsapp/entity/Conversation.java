package com.chatbot.whatsapp.entity;

import com.chatbot.whatsapp.entity.enums.ChannelType;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Representa uma sessao de conversa entre um {@link Customer} e o chatbot.
 *
 * <p>Uma conversa agrupa uma sequencia de mensagens (inbound e outbound) e
 * mantem o estado do atendimento e um pequeno contexto textual que a camada
 * de processamento pode usar para lembrar o ultimo assunto tratado.</p>
 */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ConversationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private ChannelType channel;

    /** Ultimo assunto/intencao identificado nesta conversa (texto livre, opcional). */
    @Column(name = "context", length = 1000)
    private String context;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "last_interaction_at", nullable = false)
    private Instant lastInteractionAt;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
