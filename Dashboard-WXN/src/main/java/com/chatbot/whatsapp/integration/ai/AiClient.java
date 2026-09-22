package com.chatbot.whatsapp.integration.ai;

import java.util.Optional;

/** Porta para um provedor externo de geracao de texto. */
public interface AiClient {

    Optional<String> generateReply(String customerMessage, String customerContext);
}
