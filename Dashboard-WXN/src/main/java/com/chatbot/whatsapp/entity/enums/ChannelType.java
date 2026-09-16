package com.chatbot.whatsapp.entity.enums;

/**
 * Canal de origem de uma conversa.
 *
 * <p>Hoje o unico canal suportado e o WhatsApp (via webhook simulado).
 * O enum existe para que, no futuro, outros canais (ex.: web chat, Telegram)
 * possam ser adicionados sem alterar o modelo de dados.</p>
 */
public enum ChannelType {
    WHATSAPP
}
