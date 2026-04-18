const state = {
    users: [],
    transactions: [],
    ledgerEntries: []
};

const TOKEN_KEY = "swiftpay_demo_token";
const USER_KEY = "swiftpay_demo_user";

const usersGrid = document.getElementById("users-grid");
const senderSelect = document.getElementById("sender-select");
const receiverSelect = document.getElementById("receiver-select");
const transactionsList = document.getElementById("transactions-list");
const ledgerList = document.getElementById("ledger-list");
const responseConsole = document.getElementById("response-console");
const authStatus = document.getElementById("auth-status");
const summarySender = document.getElementById("summary-sender");
const summaryReceiver = document.getElementById("summary-receiver");
const summaryAmount = document.getElementById("summary-amount");
const paymentForm = document.getElementById("payment-form");

document.getElementById("service-url").textContent = window.location.origin;
document.getElementById("refresh-users").addEventListener("click", initializeDashboard);
document.getElementById("refresh-activity").addEventListener("click", loadActivity);
document.getElementById("login-form").addEventListener("submit", handleLoginSubmit);
document.getElementById("user-form").addEventListener("submit", handleUserSubmit);
document.getElementById("payment-form").addEventListener("submit", handlePaymentSubmit);
senderSelect.addEventListener("change", updateCheckoutSummary);
receiverSelect.addEventListener("change", updateCheckoutSummary);
paymentForm.amount.addEventListener("input", updateCheckoutSummary);
paymentForm.currency.addEventListener("input", updateCheckoutSummary);

refreshAuthStatus();
initializeDashboard();

async function initializeDashboard() {
    await loadUsers();
    await loadActivity();
    updateCheckoutSummary();
}

async function loadUsers() {
    const users = await apiRequest("/v1/users");
    state.users = Array.isArray(users) ? users : [];
    renderUsers();
    renderUserOptions();
    document.getElementById("user-count").textContent = state.users.length;
}

async function loadActivity() {
    const allTransactions = [];
    const allLedgerEntries = [];

    for (const user of state.users) {
        const txPage = await apiRequest(`/v1/payments/history/${user.id}`, { suppressConsole: true });
        const ledgerPage = await apiRequest(`/v1/payments/ledger/${user.id}`, { suppressConsole: true });
        const transactions = txPage?.content ?? [];
        const ledger = ledgerPage?.content ?? [];

        transactions.forEach((transaction) => {
            if (!allTransactions.some((existing) => existing.transactionId === transaction.transactionId)) {
                allTransactions.push(transaction);
            }
        });

        ledger.forEach((entry) => {
            if (!allLedgerEntries.some((existing) => existing.entryId === entry.entryId)) {
                allLedgerEntries.push(entry);
            }
        });
    }

    state.transactions = allTransactions.sort((left, right) => new Date(right.createdAt) - new Date(left.createdAt));
    state.ledgerEntries = allLedgerEntries.sort((left, right) => new Date(right.createdAt) - new Date(left.createdAt));

    renderTransactions();
    renderLedger();
    document.getElementById("transaction-count").textContent = state.transactions.length;
    document.getElementById("ledger-count").textContent = state.ledgerEntries.length;
    document.getElementById("last-status").textContent = state.transactions[0]?.status ?? "N/A";
}

function renderUsers() {
    if (!state.users.length) {
        usersGrid.innerHTML = `<div class="empty-state">No wallets found yet.</div>`;
        return;
    }

    usersGrid.innerHTML = state.users.map((user) => `
        <article class="wallet-card">
            <h3>${escapeHtml(user.fullName)}</h3>
            <div class="wallet-meta">${escapeHtml(user.id)} | ${escapeHtml(user.email)}</div>
            <div class="wallet-meta">${escapeHtml(user.mobileNumber)} | ${escapeHtml(user.currency)}</div>
            <div class="balance-pill">${formatAmount(user.balance)} ${escapeHtml(user.currency)}</div>
        </article>
    `).join("");
}

function renderUserOptions() {
    const options = state.users.map((user) =>
        `<option value="${escapeHtml(user.id)}">${escapeHtml(user.fullName)} (${escapeHtml(user.id)})</option>`
    ).join("");

    senderSelect.innerHTML = options;
    receiverSelect.innerHTML = options;

    const loggedInUser = localStorage.getItem(USER_KEY);
    if (loggedInUser && state.users.some((user) => user.id === loggedInUser)) {
        senderSelect.value = loggedInUser;
    }
    if (state.users.length > 1 && receiverSelect.value === senderSelect.value) {
        receiverSelect.selectedIndex = senderSelect.selectedIndex === 0 ? 1 : 0;
    }
}

function renderTransactions() {
    if (!state.transactions.length) {
        transactionsList.innerHTML = `<div class="empty-state">No payments yet.</div>`;
        return;
    }

    transactionsList.innerHTML = state.transactions.slice(0, 6).map((transaction) => `
        <article class="collection-item">
            <h4>${escapeHtml(transaction.transactionId)}</h4>
            <div class="muted-text">${escapeHtml(transaction.senderId)} -> ${escapeHtml(transaction.receiverId)}</div>
            <div class="muted-text">${formatAmount(transaction.amount)} ${escapeHtml(transaction.currency)}</div>
            <span class="status-pill ${transaction.status.toLowerCase()}">${escapeHtml(transaction.status)}</span>
        </article>
    `).join("");
}

function renderLedger() {
    if (!state.ledgerEntries.length) {
        ledgerList.innerHTML = `<div class="empty-state">No ledger rows yet.</div>`;
        return;
    }

    ledgerList.innerHTML = state.ledgerEntries.slice(0, 6).map((entry) => `
        <article class="collection-item">
            <h4>${escapeHtml(entry.transactionId)}</h4>
            <div class="muted-text">${escapeHtml(entry.userId)} | ${escapeHtml(entry.entryType)}</div>
            <div class="muted-text">${formatAmount(entry.balanceBefore)} -> ${formatAmount(entry.balanceAfter)}</div>
        </article>
    `).join("");
}

function updateCheckoutSummary() {
    summarySender.textContent = senderSelect.value || "-";
    summaryReceiver.textContent = receiverSelect.value || "-";
    const amount = paymentForm.amount.value ? Number(paymentForm.amount.value).toFixed(2) : "0.00";
    const currency = paymentForm.currency.value || "USD";
    summaryAmount.textContent = `${amount} ${currency}`;
}

async function handleLoginSubmit(event) {
    event.preventDefault();
    try {
        const payload = formToJson(event.target);
        const response = await apiRequest("/v1/auth/login", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        localStorage.setItem(TOKEN_KEY, response.accessToken);
        localStorage.setItem(USER_KEY, response.userId);
        refreshAuthStatus(response);
        senderSelect.value = response.userId;
        if (receiverSelect.value === senderSelect.value && receiverSelect.options.length > 1) {
            receiverSelect.selectedIndex = senderSelect.selectedIndex === 0 ? 1 : 0;
        }
        updateCheckoutSummary();
    } catch (error) {
        writeConsole("ERROR", "/v1/auth/login", { message: error.message });
    }
}

async function handleUserSubmit(event) {
    event.preventDefault();
    try {
        const payload = formToJson(event.target);
        payload.balance = Number(payload.balance);
        await apiRequest("/v1/users", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        event.target.reset();
        event.target.currency.value = "USD";
        event.target.password.value = "demo123";
        await initializeDashboard();
    } catch (error) {
        writeConsole("ERROR", "/v1/users", { message: error.message });
    }
}

async function handlePaymentSubmit(event) {
    event.preventDefault();
    try {
        const token = localStorage.getItem(TOKEN_KEY);
        if (!token) {
            throw new Error("Login first to authorize this checkout.");
        }

        const payload = formToJson(event.target);
        payload.amount = Number(payload.amount);
        await apiRequest("/v1/payments", {
            method: "POST",
            headers: {
                Authorization: `Bearer ${token}`
            },
            body: JSON.stringify(payload)
        });
        await initializeDashboard();
    } catch (error) {
        writeConsole("ERROR", "/v1/payments", { message: error.message });
    }
}

async function apiRequest(url, options = {}) {
    const { suppressConsole = false, headers = {}, ...fetchOptions } = options;
    const response = await fetch(url, {
        headers: {
            "Content-Type": "application/json",
            ...headers
        },
        ...fetchOptions
    });

    const text = await response.text();
    const data = text ? JSON.parse(text) : null;

    if (!suppressConsole) {
        writeConsole(response.ok ? "SUCCESS" : "ERROR", url, data);
    }

    if (!response.ok) {
        throw new Error(data?.message || `Request failed with status ${response.status}`);
    }

    return data;
}

function refreshAuthStatus(response) {
    const userId = response?.userId || localStorage.getItem(USER_KEY);
    if (!userId) {
        authStatus.textContent = "Not authenticated.";
        return;
    }

    const expiresAt = response?.expiresAt ? ` Token expires at ${response.expiresAt}.` : "";
    authStatus.textContent = `Authenticated as ${userId}.${expiresAt}`;
}

function writeConsole(status, url, payload) {
    responseConsole.textContent = `${status} ${url}\n${JSON.stringify(payload, null, 2)}`;
}

function formToJson(form) {
    return Object.fromEntries(new FormData(form).entries());
}

function formatAmount(value) {
    return Number(value).toFixed(2);
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
}

window.addEventListener("error", (event) => {
    writeConsole("ERROR", "ui", { message: event.message });
});
