package com.chatbot.whatsapp.integration.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Prompt de sistema e base de conhecimento do assistente virtual.
 *
 * <p>Os textos ficam em arquivos ({@code src/main/resources/assistant/}),
 * separados da logica de negocio: o prompt pode ser revisado e a base de
 * conhecimento atualizada sem alterar codigo. A base tambem pode ficar fora do
 * pacote da aplicacao ({@code AI_KNOWLEDGE_BASE=file:...}).</p>
 *
 * <p>Comentarios HTML ({@code <!-- ... -->}) da base sao removidos antes do envio:
 * eles servem apenas como orientacao para quem mantem o arquivo.</p>
 */
@Component
public class AssistantResources {

    static final String SYSTEM_PROMPT_LOCATION = "classpath:assistant/system-prompt.txt";
    static final int MAX_KNOWLEDGE_BASE_CHARS = 12_000;

    private static final Logger log = LoggerFactory.getLogger(AssistantResources.class);
    private static final Pattern HTML_COMMENTS = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final String EMPTY_KNOWLEDGE_BASE = "(nenhuma informação cadastrada)";

    private final String systemPrompt;
    private final String knowledgeBase;

    @Autowired
    public AssistantResources(AiProperties properties, ResourceLoader resourceLoader) {
        this(readRequired(resourceLoader, SYSTEM_PROMPT_LOCATION),
                readKnowledgeBase(resourceLoader, properties.effectiveKnowledgeBase()));
    }

    AssistantResources(String systemPrompt, String knowledgeBase) {
        this.systemPrompt = systemPrompt.strip();
        this.knowledgeBase = knowledgeBase == null ? "" : knowledgeBase.strip();
    }

    /** Recursos padrao empacotados com a aplicacao. */
    public static AssistantResources fromClasspath(AiProperties properties) {
        return new AssistantResources(properties, new DefaultResourceLoader());
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    /** Conteudo util da base de conhecimento; vazio quando nada foi cadastrado. */
    public String knowledgeBase() {
        return knowledgeBase;
    }

    /** Instrucoes enviadas ao provedor: regras do assistente seguidas da base de conhecimento. */
    public String instructions() {
        return systemPrompt
                + "\n\n<base_de_conhecimento>\n"
                + (knowledgeBase.isEmpty() ? EMPTY_KNOWLEDGE_BASE : knowledgeBase)
                + "\n</base_de_conhecimento>";
    }

    private static String readRequired(ResourceLoader loader, String location) {
        Resource resource = loader.getResource(location);
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Prompt do assistente virtual nao encontrado em " + location, ex);
        }
    }

    private static String readKnowledgeBase(ResourceLoader loader, String location) {
        if (!location.startsWith("classpath:") && !location.startsWith("file:")) {
            log.warn("AI_KNOWLEDGE_BASE deve comecar com classpath: ou file:; base de conhecimento ignorada");
            return "";
        }
        Resource resource = loader.getResource(location);
        if (!resource.exists()) {
            log.warn("Base de conhecimento nao encontrada em {}; o assistente vai apenas acolher, "
                    + "pedir detalhes e encaminhar", location);
            return "";
        }
        try (InputStream input = resource.getInputStream()) {
            String content = HTML_COMMENTS.matcher(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .replaceAll("")
                    .strip();
            if (content.length() > MAX_KNOWLEDGE_BASE_CHARS) {
                log.warn("Base de conhecimento com {} caracteres; somente os primeiros {} serao usados",
                        content.length(), MAX_KNOWLEDGE_BASE_CHARS);
                content = content.substring(0, MAX_KNOWLEDGE_BASE_CHARS);
            }
            return content;
        } catch (IOException ex) {
            log.warn("Nao foi possivel ler a base de conhecimento em {}", location);
            return "";
        }
    }
}
