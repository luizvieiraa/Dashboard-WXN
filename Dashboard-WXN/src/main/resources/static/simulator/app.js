const API = {
    webhook: "/api/v1/webhook/whatsapp",
    conversation: id => `/api/v1/conversations/${id}`,
    whatsappContact: "/api/v1/whatsapp/contact"
};

const statusLabels = {
    BOT_ACTIVE: "Bot ativo",
    COLLECTING_INFORMATION: "Coletando informações",
    QUALIFIED: "Triagem concluída",
    WAITING_HUMAN: "Aguardando humano",
    HUMAN_ACTIVE: "Em atendimento",
    CLOSED: "Conversa encerrada"
};

const statusClasses = {
    BOT_ACTIVE: "status-collecting",
    COLLECTING_INFORMATION: "status-collecting",
    QUALIFIED: "status-qualified",
    WAITING_HUMAN: "status-waiting",
    HUMAN_ACTIVE: "status-active",
    CLOSED: "status-closed"
};

const state = {
    conversationId: Number(sessionStorage.getItem("wxn.simulator.conversationId")) || null,
    status: null,
    sending: false
};

const elements = {
    form: document.querySelector("#message-form"),
    input: document.querySelector("#message-input"),
    sendButton: document.querySelector("#send-button"),
    phone: document.querySelector("#phone-input"),
    messages: document.querySelector("#message-list"),
    emptyState: document.querySelector("#empty-state"),
    typing: document.querySelector("#typing"),
    conversationId: document.querySelector("#conversation-id"),
    statusBadge: document.querySelector("#status-badge"),
    statusLabel: document.querySelector("#status-label"),
    lastActivity: document.querySelector("#last-activity"),
    handoffNote: document.querySelector("#handoff-note"),
    newSession: document.querySelector("#new-session"),
    whatsappButton: document.querySelector("#whatsapp-button"),
    composerHint: document.querySelector("#composer-hint"),
    toast: document.querySelector("#toast")
};

function generatePhone() {
    const suffix = String(Math.floor(10000000 + Math.random() * 90000000));
    return `55119${suffix}`;
}

function startNewSession() {
    state.conversationId = null;
    state.status = null;
    sessionStorage.removeItem("wxn.simulator.conversationId");
    elements.phone.value = generatePhone();
    elements.phone.disabled = false;
    elements.messages.replaceChildren(elements.emptyState);
    elements.emptyState.hidden = false;
    elements.conversationId.textContent = "Nova conversa";
    elements.lastActivity.textContent = "—";
    elements.handoffNote.hidden = true;
    elements.input.disabled = false;
    elements.sendButton.disabled = false;
    elements.input.value = "";
    updateStatus(null);
    elements.input.focus();
}

async function sendMessage(event) {
    event.preventDefault();
    if (state.sending) return;

    const phone = elements.phone.value.replace(/\D/g, "");
    const message = elements.input.value.trim();
    if (!/^\d{8,15}$/.test(phone)) {
        showToast("Informe um telefone com 8 a 15 dígitos.");
        elements.phone.focus();
        return;
    }
    if (!message) return;

    state.sending = true;
    elements.phone.value = phone;
    elements.phone.disabled = true;
    elements.input.value = "";
    resizeComposer();
    appendMessage({direction: "INBOUND", content: message, createdAt: new Date().toISOString()}, true);
    setSending(true);

    try {
        const result = await request(API.webhook, {
            method: "POST",
            body: JSON.stringify({phone, message})
        });
        state.conversationId = result.conversationId;
        sessionStorage.setItem("wxn.simulator.conversationId", String(result.conversationId));
        await loadConversation(result.conversationId);
    } catch (error) {
        if (!state.conversationId) elements.phone.disabled = false;
        showToast(error.message);
    } finally {
        state.sending = false;
        setSending(false);
        elements.input.focus();
    }
}

async function loadConversation(id) {
    const conversation = await request(API.conversation(id));
    elements.phone.value = conversation.customerPhone;
    elements.phone.disabled = true;
    elements.conversationId.textContent = `Conversa #${conversation.id}`;
    elements.lastActivity.textContent = formatDateTime(conversation.lastInteractionAt);
    renderMessages(conversation.messages || []);
    updateStatus(conversation.status);
}

function renderMessages(messages) {
    elements.emptyState.hidden = messages.length > 0;
    elements.messages.replaceChildren();
    if (messages.length === 0) {
        elements.messages.append(elements.emptyState);
        return;
    }
    messages.forEach(message => appendMessage(message, false));
    scrollToLatest();
}

function appendMessage(message, pending) {
    if (elements.emptyState.isConnected) elements.emptyState.remove();

    const row = document.createElement("article");
    const inbound = message.direction === "INBOUND";
    row.className = `message-row ${inbound ? "inbound" : "outbound"}`;
    if (pending) row.dataset.pending = "true";

    const content = document.createElement("div");
    content.className = "message-content";

    const author = document.createElement("span");
    author.className = "message-author";
    author.textContent = inbound ? "Você" : "Assistente WXN";

    const bubble = document.createElement("div");
    bubble.className = "message-bubble";
    bubble.textContent = message.content;

    const time = document.createElement("time");
    time.className = "message-time";
    time.dateTime = message.createdAt;
    time.textContent = pending ? "Enviando..." : formatTime(message.createdAt);

    content.append(author, bubble, time);
    row.append(content);
    elements.messages.append(row);
    scrollToLatest();
}

function updateStatus(status) {
    state.status = status;
    const label = statusLabels[status] || "Aguardando início";
    elements.statusLabel.textContent = label;
    elements.statusBadge.textContent = status ? label : "Novo";
    elements.statusBadge.className = `status-badge ${statusClasses[status] || "status-new"}`;
    elements.handoffNote.hidden = status !== "WAITING_HUMAN" && status !== "HUMAN_ACTIVE";

    const closed = status === "CLOSED";
    elements.input.disabled = closed;
    elements.sendButton.disabled = closed;
    elements.composerHint.textContent = closed
        ? "Esta conversa foi encerrada. Inicie uma nova simulação para continuar."
        : status === "WAITING_HUMAN" || status === "HUMAN_ACTIVE"
            ? "O bot está pausado enquanto o atendimento humano assume a conversa."
            : "Enter para enviar · Shift + Enter para quebrar linha";
}

function setSending(sending) {
    elements.typing.hidden = !sending;
    const unavailable = sending || state.status === "CLOSED";
    elements.sendButton.disabled = unavailable;
    elements.input.disabled = unavailable;
    if (sending) scrollToLatest();
}

async function request(url, options = {}) {
    const response = await fetch(url, {
        headers: {"Content-Type": "application/json", ...(options.headers || {})},
        ...options
    });
    if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.message || `Não foi possível concluir a operação (${response.status}).`);
    }
    return response.json();
}

async function loadWhatsAppContact() {
    try {
        const contact = await request(API.whatsappContact);
        if (contact.available && contact.url) {
            elements.whatsappButton.href = contact.url;
            elements.whatsappButton.hidden = false;
        }
    } catch (_) {
        elements.whatsappButton.hidden = true;
    }
}

function resizeComposer() {
    elements.input.style.height = "auto";
    elements.input.style.height = `${Math.min(elements.input.scrollHeight, 130)}px`;
}

function scrollToLatest() {
    requestAnimationFrame(() => {
        elements.messages.scrollTop = elements.messages.scrollHeight;
    });
}

function showToast(message) {
    elements.toast.textContent = message;
    elements.toast.hidden = false;
    window.clearTimeout(showToast.timeout);
    showToast.timeout = window.setTimeout(() => { elements.toast.hidden = true; }, 3500);
}

function formatTime(value) {
    return new Intl.DateTimeFormat("pt-BR", {hour: "2-digit", minute: "2-digit"}).format(new Date(value));
}

function formatDateTime(value) {
    return new Intl.DateTimeFormat("pt-BR", {
        day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit"
    }).format(new Date(value));
}

elements.form.addEventListener("submit", sendMessage);
elements.input.addEventListener("input", resizeComposer);
elements.input.addEventListener("keydown", event => {
    if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        elements.form.requestSubmit();
    }
});
elements.phone.addEventListener("input", () => {
    elements.phone.value = elements.phone.value.replace(/\D/g, "");
});
elements.newSession.addEventListener("click", startNewSession);
document.querySelectorAll("[data-message]").forEach(button => {
    button.addEventListener("click", () => {
        elements.input.value = button.dataset.message;
        resizeComposer();
        elements.input.focus();
    });
});

if (state.conversationId) {
    loadConversation(state.conversationId).catch(() => startNewSession());
} else {
    startNewSession();
}
loadWhatsAppContact();
