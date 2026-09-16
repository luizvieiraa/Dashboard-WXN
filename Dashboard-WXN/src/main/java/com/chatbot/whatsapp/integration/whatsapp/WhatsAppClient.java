package com.chatbot.whatsapp.integration.whatsapp;

/**
 * Abstracao para o envio de mensagens de volta ao cliente via WhatsApp.
 *
 * <p>A camada de negocio (services) depende apenas desta interface. Isso
 * permite que, quando a integracao real for definida com o cliente, uma
 * nova implementacao (ex.: {@code MetaWhatsAppClient}, usando WebClient/RestClient
 * para chamar a Meta Cloud API) seja adicionada e habilitada por configuracao
 * (propriedade {@code whatsapp.enabled}), sem alterar nenhuma regra de negocio.</p>
 */
public interface WhatsAppClient {

    /**
     * Envia (ou simula o envio de) uma mensagem de texto para o numero informado.
     *
     * @param phoneNumber numero de telefone do destinatario (mesmo formato recebido no webhook)
     * @param text        conteudo da mensagem a ser enviada
     */
    void sendMessage(String phoneNumber, String text);
}
