package com.chatbot.whatsapp.entity;

import com.chatbot.whatsapp.entity.enums.ChannelType;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import com.chatbot.whatsapp.exception.InvalidConversationStateException;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

    @Builder.Default
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chatbot_id", nullable = false)
    private Chatbot chatbot = Chatbot.standard();

    @OneToOne(mappedBy = "conversation", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private Attendance attendance;

    public void assume(Attendant attendant) {
        requireStatus(ConversationStatus.WAITING_HUMAN);
        Instant now = Instant.now();
        attendance = new Attendance(this, attendant, now);
        assignedTo = attendant.getName();
        assignedAt = now;
        status = ConversationStatus.HUMAN_ACTIVE;
        recordInteraction("HUMAN_ATTENDANCE_STARTED");
    }

    public void requireHumanAttendance() {
        requireStatus(ConversationStatus.HUMAN_ACTIVE);
    }

    public void recordInteraction(String context) {
        if (status == ConversationStatus.CLOSED) throw invalidState();
        this.context = context;
        lastInteractionAt = Instant.now();
    }

    public void changeAutomatedStatus(ConversationStatus next) {
        if (status == ConversationStatus.CLOSED || status == ConversationStatus.HUMAN_ACTIVE
                || status == ConversationStatus.WAITING_HUMAN
                || next == null || next == ConversationStatus.HUMAN_ACTIVE || next == ConversationStatus.CLOSED) {
            throw invalidState();
        }
        status = next;
    }

    public void close() {
        closeAt(Instant.now(), "CONVERSATION_CLOSED");
        lastInteractionAt = closedAt;
    }

    public void closeForInactivity(Instant now) {
        if (status != ConversationStatus.BOT_ACTIVE && status != ConversationStatus.COLLECTING_INFORMATION
                && status != ConversationStatus.QUALIFIED) throw invalidState();
        closeAt(now, "AUTO_CLOSED_INACTIVITY");
    }

    private void closeAt(Instant now, String context) {
        java.util.Objects.requireNonNull(now, "Data de encerramento obrigatoria");
        if (status == ConversationStatus.CLOSED) throw invalidState();
        if (attendance != null) attendance.close(now);
        status = ConversationStatus.CLOSED;
        closedAt = now;
        this.context = context;
    }

    private void requireStatus(ConversationStatus expected) {
        if (status != expected) throw invalidState();
    }

    private InvalidConversationStateException invalidState() {
        return new InvalidConversationStateException(
                "Operacao invalida para conversa em " + status);
    }
}
