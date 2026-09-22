-- V4: idempotencia das mensagens recebidas do WhatsApp.
--
-- A coluna external_id ja existia (V1) para guardar o id da mensagem no
-- provedor. Com o webhook real da Meta ela passa a ser efetivamente usada:
-- a Meta reentrega uma notificacao sempre que nao recebe 200, e reprocessar a
-- mesma mensagem faria o bot responder duas vezes e a coleta da triagem
-- avancar de etapa indevidamente.
--
-- O indice e UNIQUE para garantir a protecao mesmo com duas entregas
-- simultaneas (a segunda falha na gravacao e e descartada pela aplicacao).
-- Multiplos NULL sao permitidos em um indice unico, entao as mensagens sem
-- origem externa (respostas do bot, atendimento humano e o simulador local)
-- continuam funcionando normalmente.

CREATE UNIQUE INDEX ux_messages_external_id ON messages (external_id);
