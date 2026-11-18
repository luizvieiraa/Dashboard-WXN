package com.chatbot.whatsapp.integration.whatsapp;

import com.chatbot.whatsapp.integration.ProviderStatus;

/**
 * Abstracao para o envio de mensagens de volta ao cliente via WhatsApp.
 *
 * <p>A camada de negocio (services) depende apenas desta interface. Isso
 * permite alternar entre o envio simulado e a Meta Cloud API pela propriedade
 * {@code whatsapp.enabled}, sem alterar as regras de negocio.</p>
 */
public interface WhatsAppClient {

    /**
     * Envia (ou simula o envio de) uma mensagem de texto para o numero informado.
     *
     * @param phoneNumber numero de telefone do destinatario (mesmo formato recebido no webhook)
     * @param text        conteudo da mensagem a ser enviada
     * @return resultado confirmado pelo provedor, incluindo o id externo
     */
    WhatsAppSendResult sendMessage(String phoneNumber, String text);

    /** Resultado do envio mais recente ao provedor; o envio simulado nao registra nada. */
    default ProviderStatus.Snapshot status() {
        return ProviderStatus.Snapshot.NONE;
    }
}
