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
        current.updateAndGet(last -> new Snapshot(Instant.now(), last.lastFailureAt(), last.lastFailure(), false));
    }

    public void recordFailure(String reason) {
        current.updateAndGet(last -> new Snapshot(last.lastSuccessAt(), Instant.now(), reason, true));
    }

    public Snapshot snapshot() {
        return current.get();
    }

    /**
     * @param failing {@code true} quando a chamada mais recente falhou. Fica
     *                gravado, e nao comparado pelos horarios, porque sucesso e
     *                falha podem cair no mesmo instante do relogio.
     */
    public record Snapshot(Instant lastSuccessAt, Instant lastFailureAt, String lastFailure, boolean failing) {

        public static final Snapshot NONE = new Snapshot(null, null, null, false);
    }
}
