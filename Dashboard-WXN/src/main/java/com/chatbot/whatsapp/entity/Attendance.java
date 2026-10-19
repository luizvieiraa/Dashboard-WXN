package com.chatbot.whatsapp.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

/** Atendimento humano vinculado a uma conversa e a um operador. */
@Entity
@Table(name = "attendances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, unique = true)
    private Conversation conversation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendant_id", nullable = false)
    private Attendant attendant;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "closed_at")
    private Instant closedAt;

    Attendance(Conversation conversation, Attendant attendant, Instant now) {
        this.conversation = java.util.Objects.requireNonNull(conversation);
        this.attendant = java.util.Objects.requireNonNull(attendant);
        this.startedAt = java.util.Objects.requireNonNull(now);
    }

    void close(Instant now) {
        if (closedAt != null) throw new IllegalStateException("Atendimento ja encerrado");
        closedAt = now;
    }
}
