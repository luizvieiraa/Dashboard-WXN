package com.chatbot.whatsapp.entity;

import com.chatbot.whatsapp.entity.enums.TriageCategory;
import com.chatbot.whatsapp.entity.enums.TriagePriority;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Dados estruturados extraidos de uma conversa para qualificacao comercial
 * ou encaminhamento a atendimento humano.
 */
@Entity
@Table(name = "triages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Triage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, unique = true)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20)
    private TriageCategory category;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TriagePriority priority = TriagePriority.MEDIUM;

    @Column(name = "subject")
    private String subject;

    @Column(name = "customer_need", columnDefinition = "TEXT")
    private String customerNeed;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Builder.Default
    @Column(name = "requires_human", nullable = false)
    private boolean requiresHuman = false;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "triage_missing_information", joinColumns = @JoinColumn(name = "triage_id"))
    @Column(name = "field_name", nullable = false, length = 100)
    private Set<String> missingInformation = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
