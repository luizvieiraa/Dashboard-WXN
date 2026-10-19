package com.chatbot.whatsapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

/** Operador humano responsavel por atendimentos. */
@Entity
@Table(name = "attendants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;

    public Attendant(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome do atendente obrigatorio");
        }
        this.name = name.trim();
    }
}
