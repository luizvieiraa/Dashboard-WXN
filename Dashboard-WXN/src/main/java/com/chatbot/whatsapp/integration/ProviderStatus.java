package com.chatbot.whatsapp.integration;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ultimo resultado observado em um provedor externo (IA ou WhatsApp), em memoria.
 *
 * <p>Serve para o painel de integracoes mostrar se a chamada mais recente deu
 * certo e, se nao deu, o motivo resumido, sem precisar procurar nos logs. O
 * motivo nunca traz credenciais nem conteudo de conversa.</p>
 */
public final class ProviderStatus {

    private final AtomicReference<Snapshot> current = new AtomicReference<>(Snapshot.NONE);

    public void recordSuccess() {
        current.updateAndGet(last -> new Snapshot(Instant.now(), last.lastFailureAt(), last.lastFailure()));
    }

    public void recordFailure(String reason) {
        current.updateAndGet(last -> new Snapshot(last.lastSuccessAt(), Instant.now(), reason));
    }

    public Snapshot snapshot() {
        return current.get();
    }

    public record Snapshot(Instant lastSuccessAt, Instant lastFailureAt, String lastFailure) {

        public static final Snapshot NONE = new Snapshot(null, null, null);

        /** {@code true} quando a chamada mais recente terminou em falha. */
        public boolean failing() {
            return lastFailureAt != null && (lastSuccessAt == null || lastFailureAt.isAfter(lastSuccessAt));
        }
    }
}
