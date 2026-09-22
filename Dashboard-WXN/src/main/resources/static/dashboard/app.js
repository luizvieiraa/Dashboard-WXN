const API = {
    summary: "/api/v1/dashboard/summary",
    triages: "/api/v1/dashboard/triages"
};

const state = {
    triages: [],
    selectedTriage: null,
    action: null
};

const elements = {
    filters: document.querySelector("#filters"),
    clearFilters: document.querySelector("#clear-filters"),
    refreshButton: document.querySelector("#refresh-button"),
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
    wrapper.append(
        strong(triage.customerName || "Nome não informado"),
        small(triage.companyName || triage.customerPhone)
    );
    return wrapper;
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
        wrapper.append(actionButton("Assumir", "claim", triage));
    } else if (triage.conversationStatus === "HUMAN_ACTIVE") {
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

function openAction(action, triage) {
    state.action = action;
    state.selectedTriage = triage;
    elements.dialogError.hidden = true;
    elements.dialogContext.textContent = `${triage.customerName || triage.customerPhone} · ${triage.subject || triage.customerNeed || "Sem assunto"}`;
    elements.dialogField.hidden = action === "close";
    elements.dialogInput.value = "";

    if (action === "claim") {
        elements.dialogTitle.textContent = "Assumir conversa";
        elements.dialogLabel.textContent = "Nome do atendente";
        elements.dialogInput.type = "text";
        elements.dialogInput.placeholder = "Ex.: Carlos Lima";
        elements.dialogInput.maxLength = 255;
        elements.dialogSubmit.textContent = "Assumir atendimento";
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

    elements.dialogSubmit.disabled = true;
    elements.dialogError.hidden = true;
    try {
        await request(current.url, {
            method: "POST",
            body: current.body ? JSON.stringify(current.body) : undefined
        });
        elements.dialog.close();
        showToast(current.success);
        await loadDashboard();
    } catch (error) {
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

function showToast(message) {
    elements.toast.textContent = message;
    elements.toast.hidden = false;
    window.setTimeout(() => { elements.toast.hidden = true; }, 3200);
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
