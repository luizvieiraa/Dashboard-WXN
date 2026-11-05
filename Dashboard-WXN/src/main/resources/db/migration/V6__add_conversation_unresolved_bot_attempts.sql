-- V6: contador de respostas automaticas sem solucao.
--
-- Quando o bot responde varias vezes seguidas sem resolver a mensagem
-- (intencao nao reconhecida ou pedido de mais informacoes), a conversa e
-- encaminhada para atendimento humano (CONVERSATION_MAX_UNRESOLVED_ATTEMPTS).
--
-- Alteracao aditiva: coluna nova com valor padrao, sem mudar dados existentes.
-- Conversas ja gravadas comecam com zero tentativas.

ALTER TABLE conversations ADD COLUMN unresolved_bot_attempts INTEGER NOT NULL DEFAULT 0;
