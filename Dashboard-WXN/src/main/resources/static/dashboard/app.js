const API = {
    summary: "/api/v1/dashboard/summary",
    triages: "/api/v1/dashboard/triages",
    whatsappContact: "/api/v1/whatsapp/contact"
};

const state = {
    triages: [],
    selectedTriage: null,
    action: null
};

/* Link montado pelo backend a partir do telefone normalizado (somente dígitos). */
const WHATSAPP_CONVERSATION_URL = /^https:\/\/wa\.me\/\d{8,15}$/;
const INVALID_PHONE_MESSAGE = "Não foi possível abrir a conversa no WhatsApp: o telefone deste cliente "
    + "está ausente ou é inválido. Confira o número antes de continuar o atendimento.";

const elements = {
    filters: document.querySelector("#filters"),
    clearFilters: document.querySelector("#clear-filters"),
    refreshButton: document.querySelector("#refresh-button"),
    whatsappButton: document.querySelector("#whatsapp-button"),
    rows: document.querySelector("#triage-rows"),
    feedback: document.querySelector("#feedback"),
    resultCount: document.querySelector("#result-count"),
    lastUpdated: document.querySelector("#last-updated"),
    queueAlert: document.querySelector("#queue-alert"),
    dialog: document.querySelector("#action-dialog"),
    dialogForm: document.querySelector("#action-form"),
    dialogTitle: document.querySelector("#dialog-title"),
    dialogContext: document.querySelector("#dialog-context"),
    dialogField: document.querySelector("#dialog-field"),
    dialogLabel: document.querySelector("#dialog-label"),
    dialogInput: document.querySelector("#dialog-input"),
    dialogError: document.querySelector("#dialog-error"),
    dialogSubmit: document.querySelector("#dialog-submit"),
    dialogClose: document.querySelector("#dialog-close"),
    dialogCancel: document.querySelector("#dialog-cancel"),
    toast: document.querySelector("#toast")
};

const labels = {
    category: {
        INFORMATION: "Informação",
        COMPLAINT: "Reclamação",
        OTHER: "Outro"
    },
    priority: {
        LOW: "Baixa",
        MEDIUM: "Média",
        HIGH: "Alta",
        URGENT: "Urgente"
    },
    status: {
        BOT_ACTIVE: "Bot ativo",
        COLLECTING_INFORMATION: "Coletando informações",
        QUALIFIED: "Qualificado",
        WAITING_HUMAN: "Aguardando humano",
        HUMAN_ACTIVE: "Em atendimento",
        CLOSED: "Encerrado"
    }
};

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

async function loadDashboard() {
    setLoading(true);
    try {
        const params = new URLSearchParams(new FormData(elements.filters));
        for (const [key, value] of [...params.entries()]) {
            if (!value) params.delete(key);
        }
        const [summary, triages] = await Promise.all([
            request(API.summary),
            request(`${API.triages}?${params}`)
        ]);
        state.triages = triages;
        renderSummary(summary);
        renderTriages(triages);
        elements.lastUpdated.textContent = `Atualizado às ${formatTime(summary.generatedAt)}`;
    } catch (error) {
        showFeedback(error.message, true);
    } finally {
        setLoading(false);
    }
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

function renderSummary(summary) {
    document.querySelectorAll("[data-metric]").forEach(element => {
        element.textContent = formatNumber(summary[element.dataset.metric] ?? 0);
    });

    const waiting = summary.waitingHuman || 0;
    elements.queueAlert.hidden = waiting === 0;
    elements.queueAlert.textContent = waiting === 1
        ? "1 conversa precisa de atendimento"
        : `${waiting} conversas precisam de atendimento`;
}

function renderTriages(triages) {
    elements.rows.replaceChildren(...triages.map(createTriageRow));
    elements.resultCount.textContent = `${triages.length} ${triages.length === 1 ? "resultado" : "resultados"}`;
    showFeedback(triages.length ? "" : "Nenhuma triagem encontrada para os filtros selecionados.");
}

function createTriageRow(triage) {
    const row = document.createElement("tr");
    row.append(
        cell("Cliente", customerContent(triage), "customer-cell"),
        cell("Assunto", subjectContent(triage), "subject-cell"),
        cell("Categoria", badge(labels.category[triage.category] || triage.category, `badge-${triage.category.toLowerCase()}`)),
        cell("Prioridade", text(labels.priority[triage.priority] || triage.priority), `priority-${triage.priority.toLowerCase()}`),
        cell("Status", statusContent(triage.conversationStatus)),
        cell("Ação", actionContent(triage), "actions")
    );
    return row;
}

function customerContent(triage) {
    const wrapper = document.createElement("div");
    wrapper.className = "customer";

    const details = document.createElement("div");
    details.append(
        strong(triage.customerName || "Nome não informado"),
        small(triage.companyName || triage.customerPhone)
    );

    wrapper.append(avatar(triage.customerName), details);
    return wrapper;
}

function avatar(name) {
    const element = text(initials(name));
    element.className = "avatar";
    element.setAttribute("aria-hidden", "true");
    return element;
}

function initials(name) {
    const words = (name || "").trim().split(/\s+/).filter(Boolean);
    if (words.length === 0) return "?";

    const firstLetter = word => [...word][0].toUpperCase();
    return words.length === 1
        ? firstLetter(words[0])
        : firstLetter(words[0]) + firstLetter(words[words.length - 1]);
}

function subjectContent(triage) {
    const wrapper = document.createElement("div");
    wrapper.append(
        strong(triage.subject || triage.customerNeed || "Sem assunto"),
        small(`Conversa #${triage.conversationId} · ${formatDate(triage.lastInteractionAt)}`)
    );
    return wrapper;
}

function badge(content, className) {
    const element = text(content);
    element.className = `badge ${className}`;
    return element;
}

function statusContent(status) {
    const element = text(labels.status[status] || status);
    element.className = `status status-${status.toLowerCase().replaceAll("_", "-")}`;
    return element;
}

function actionContent(triage) {
    const wrapper = document.createElement("div");
    wrapper.className = "actions";
    if (triage.conversationStatus === "WAITING_HUMAN") {
        wrapper.append(conversationButton(triage));
        wrapper.append(actionButton("Assumir", "claim", triage));
    } else if (triage.conversationStatus === "HUMAN_ACTIVE") {
        wrapper.append(conversationButton(triage));
        wrapper.append(actionButton("Responder", "reply", triage));
        wrapper.append(actionButton("Encerrar", "close", triage, "button-danger"));
    } else {
        const empty = text("—");
        empty.setAttribute("aria-label", "Nenhuma ação disponível");
        wrapper.append(empty);
    }
    return wrapper;
}

function actionButton(label, action, triage, extraClass = "") {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `button button-secondary ${extraClass}`.trim();
    button.textContent = label;
    button.addEventListener("click", () => openAction(action, triage));
    return button;
}

/* "Ir para a conversa": continua o atendimento direto no WhatsApp do cliente. */
function conversationButton(triage) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "button button-whatsapp";
    button.textContent = "Ir para a conversa";
    button.addEventListener("click", () => goToConversation(triage));
    return button;
}

function isWhatsAppConversationUrl(url) {
    return typeof url === "string" && WHATSAPP_CONVERSATION_URL.test(url);
}

function goToConversation(triage) {
    if (!isWhatsAppConversationUrl(triage.customerWhatsAppUrl)) {
        showToast(INVALID_PHONE_MESSAGE, true);
        return;
    }
    if (triage.conversationStatus === "WAITING_HUMAN") {
        // Antes de sair do dashboard, o atendente assume a conversa: o bot é
        // pausado somente neste atendimento e não responde junto com ele.
        openAction("claimAndOpen", triage);
        return;
    }
    window.open(triage.customerWhatsAppUrl, "_blank", "noopener");
}

/*
 * A aba é aberta no próprio clique de confirmação (antes da chamada à API) para
 * não ser barrada pelo bloqueador de pop-ups; o endereço do WhatsApp só é
 * carregado depois que o atendimento for assumido com sucesso.
 */
function prepareWhatsAppTab() {
    const tab = window.open("", "_blank");
    if (!tab) return null;
    try {
        tab.opener = null;
        tab.document.title = "Abrindo o WhatsApp...";
        tab.document.body.textContent = "Abrindo a conversa no WhatsApp...";
    } catch (_) {
        // Sem acesso ao conteúdo da aba: a navegação continua funcionando.
    }
    return tab;
}

function openPreparedWhatsAppTab(tab, url) {
    if (!isWhatsAppConversationUrl(url)) {
        if (tab) tab.close();
        return false;
    }
    if (tab && !tab.closed) {
        tab.location.replace(url);
        return true;
    }
    return false;
}

function openAction(action, triage) {
    state.action = action;
    state.selectedTriage = triage;
    elements.dialogError.hidden = true;
    elements.dialogContext.textContent = `${triage.customerName || triage.customerPhone} · ${triage.subject || triage.customerNeed || "Sem assunto"}`;
    elements.dialogField.hidden = action === "close";
    elements.dialogInput.disabled = action === "close";
    elements.dialogInput.required = action !== "close";
    elements.dialogInput.value = "";

    if (action === "claim") {
        elements.dialogTitle.textContent = "Assumir conversa";
        elements.dialogLabel.textContent = "Nome do atendente";
        elements.dialogInput.type = "text";
        elements.dialogInput.placeholder = "Ex.: Carlos Lima";
        elements.dialogInput.maxLength = 255;
        elements.dialogSubmit.textContent = "Assumir atendimento";
    } else if (action === "claimAndOpen") {
        elements.dialogTitle.textContent = "Ir para a conversa";
        elements.dialogContext.textContent += ". Ao confirmar, você assume o atendimento, as respostas "
            + "automáticas são pausadas nesta conversa e o WhatsApp abre em uma nova aba.";
        elements.dialogLabel.textContent = "Nome do atendente";
        elements.dialogInput.type = "text";
        elements.dialogInput.placeholder = "Ex.: Carlos Lima";
        elements.dialogInput.maxLength = 255;
        elements.dialogSubmit.textContent = "Assumir e abrir o WhatsApp";
    } else if (action === "reply") {
        elements.dialogTitle.textContent = "Responder cliente";
        elements.dialogLabel.textContent = "Mensagem";
        elements.dialogInput.type = "text";
        elements.dialogInput.placeholder = "Digite a resposta do atendente";
        elements.dialogInput.maxLength = 4096;
        elements.dialogSubmit.textContent = "Enviar resposta";
    } else {
        elements.dialogTitle.textContent = "Encerrar conversa";
        elements.dialogContext.textContent += ". Confirme o encerramento deste atendimento.";
        elements.dialogSubmit.textContent = "Encerrar conversa";
    }

    elements.dialog.showModal();
    if (action !== "close") elements.dialogInput.focus();
}

async function submitAction(event) {
    event.preventDefault();
    const triage = state.selectedTriage;
    if (!triage) return;

    const value = elements.dialogInput.value.trim();
    if (state.action !== "close" && !value) {
        elements.dialogError.textContent = "Preencha este campo para continuar.";
        elements.dialogError.hidden = false;
        return;
    }

    const actions = {
        claim: {
            url: `/api/v1/conversations/${triage.conversationId}/human/claim`,
            body: {attendant: value},
            success: "Atendimento assumido com sucesso."
        },
        claimAndOpen: {
            url: `/api/v1/conversations/${triage.conversationId}/human/claim`,
            body: {attendant: value},
            success: "Atendimento assumido. Continue a conversa na aba do WhatsApp."
        },
        reply: {
            url: `/api/v1/conversations/${triage.conversationId}/human/reply`,
            body: {message: value},
            success: "Resposta enviada ao cliente."
        },
        close: {
            url: `/api/v1/conversations/${triage.conversationId}/close`,
            body: null,
            success: "Conversa encerrada."
        }
    };
    const current = actions[state.action];
    const opensWhatsApp = state.action === "claimAndOpen";
    const whatsappTab = opensWhatsApp ? prepareWhatsAppTab() : null;

    elements.dialogSubmit.disabled = true;
    elements.dialogError.hidden = true;
    try {
        await request(current.url, {
            method: "POST",
            body: current.body ? JSON.stringify(current.body) : undefined
        });
        elements.dialog.close();
        if (opensWhatsApp && !openPreparedWhatsAppTab(whatsappTab, triage.customerWhatsAppUrl)) {
            showToast("Atendimento assumido. O navegador bloqueou a nova aba: "
                + "clique em “Ir para a conversa” para abrir o WhatsApp.");
        } else {
            showToast(current.success);
        }
        await loadDashboard();
    } catch (error) {
        if (whatsappTab && !whatsappTab.closed) whatsappTab.close();
        elements.dialogError.textContent = error.message;
        elements.dialogError.hidden = false;
    } finally {
        elements.dialogSubmit.disabled = false;
    }
}

function cell(label, content, className = "") {
    const element = document.createElement("td");
    element.dataset.label = label;
    element.className = className;
    element.append(content);
    return element;
}

function text(content) {
    const element = document.createElement("span");
    element.textContent = content;
    return element;
}

function strong(content) {
    const element = document.createElement("strong");
    element.textContent = content;
    return element;
}

function small(content) {
    const element = document.createElement("small");
    element.textContent = content;
    return element;
}

function setLoading(loading) {
    elements.refreshButton.disabled = loading;
    elements.refreshButton.setAttribute("aria-busy", String(loading));
    if (loading && state.triages.length === 0) showFeedback("Carregando triagens...");
}

function showFeedback(message, error = false) {
    elements.feedback.hidden = !message;
    elements.feedback.textContent = message;
    elements.feedback.classList.toggle("error", error);
}

function showToast(message, error = false) {
    elements.toast.textContent = message;
    elements.toast.classList.toggle("toast-error", error);
    elements.toast.setAttribute("role", error ? "alert" : "status");
    elements.toast.hidden = false;
    window.clearTimeout(showToast.timeout);
    showToast.timeout = window.setTimeout(() => { elements.toast.hidden = true; }, error ? 6500 : 3200);
}

function formatNumber(value) {
    return new Intl.NumberFormat("pt-BR").format(value);
}

function formatTime(value) {
    return new Intl.DateTimeFormat("pt-BR", {hour: "2-digit", minute: "2-digit"}).format(new Date(value));
}

function formatDate(value) {
    if (!value) return "sem data";
    return new Intl.DateTimeFormat("pt-BR", {
        day: "2-digit",
        month: "short",
        hour: "2-digit",
        minute: "2-digit"
    }).format(new Date(value));
}

elements.filters.addEventListener("submit", event => {
    event.preventDefault();
    loadDashboard();
});
elements.clearFilters.addEventListener("click", () => {
    elements.filters.reset();
    loadDashboard();
});
elements.refreshButton.addEventListener("click", loadDashboard);
elements.dialogForm.addEventListener("submit", submitAction);
elements.dialogClose.addEventListener("click", () => elements.dialog.close());
elements.dialogCancel.addEventListener("click", () => elements.dialog.close());

loadDashboard();
loadWhatsAppContact();
