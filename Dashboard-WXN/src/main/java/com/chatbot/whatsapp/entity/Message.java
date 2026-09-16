package com.chatbot.whatsapp.entity;

import com.chatbot.whatsapp.entity.enums.MessageDirection;
import com.chatbot.whatsapp.entity.enums.MessageStatus;
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
 * Uma mensagem individual (recebida do cliente ou enviada pelo chatbot)
 * dentro de uma {@link Conversation}.
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private MessageDirection direction;

    // Sem @Lob de proposito: no PostgreSQL, Hibernate pode mapear @Lob em
    // String para o tipo "oid" (large object) em vez de "text", o que
    // quebraria a validacao do schema contra a coluna TEXT criada pelo
    // Flyway (ver V1__init.sql). Uma coluna TEXT comum ja comporta
    // mensagens de qualquer tamanho, sem necessidade de @Lob.
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MessageStatus status;

    /** Identificador da mensagem no provedor externo (ex.: WhatsApp), quando existir. */
    @Column(name = "external_id")
    private String externalId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
