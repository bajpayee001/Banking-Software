// Global state
let currentReports = [];
let allAccounts = [];

document.addEventListener("DOMContentLoaded", () => {
    generateRandomTxnId();
    loadAllData();
});

function switchTab(tabId) {
    document.querySelectorAll(".tab-btn").forEach(btn => btn.classList.remove("active"));
    document.querySelectorAll(".tab-content").forEach(content => content.classList.remove("active"));

    const activeBtn = Array.from(document.querySelectorAll(".tab-btn")).find(btn =>
        btn.getAttribute("onclick").includes(tabId)
    );
    if (activeBtn) activeBtn.classList.add("active");

    const targetSection = document.getElementById(tabId);
    if (targetSection) targetSection.classList.add("active");

    if (tabId === "tab-reports" && currentReports.length === 0) {
        loadAllReports();
    } else if (tabId === "tab-audit") {
        loadAuditAndFailedLogs();
    }
}

function generateRandomTxnId() {
    const randomHex = Math.random().toString(36).substring(2, 8).toUpperCase();
    document.getElementById("txn-id").value = "TXN" + Date.now().toString().slice(-4) + randomHex;
}

function loadAllData() {
    loadAccounts();
    loadAuditAndFailedLogs();
}

// 1. Accounts API
async function loadAccounts() {
    try {
        const res = await fetch("/api/accounts");
        allAccounts = await res.json();

        // Render account cards
        const grid = document.getElementById("accounts-grid");
        grid.innerHTML = "";

        const srcSelect = document.getElementById("txn-source");
        const destSelect = document.getElementById("txn-dest");
        srcSelect.innerHTML = "";
        destSelect.innerHTML = "";

        allAccounts.forEach(acc => {
            const isFrozen = acc.status !== "ACTIVE";
            const card = document.createElement("div");
            card.className = `account-card ${isFrozen ? "frozen" : ""}`;
            card.innerHTML = `
                <div class="acc-header">
                    <div>
                        <div class="acc-id">${acc.accountId}</div>
                        <div class="acc-num">${acc.accountNumber}</div>
                    </div>
                    <span class="tag ${isFrozen ? 'tag-warning' : 'tag-success'}">${acc.status}</span>
                </div>
                <div class="acc-customer">👤 ${acc.customerName}</div>
                <div class="acc-balance">₹${Number(acc.balance).toLocaleString("en-IN", {minimumFractionDigits: 2})}</div>
                <div class="acc-meta">
                    <span style="color: var(--text-muted)">Currency: ${acc.currency}</span>
                    <button class="btn btn-secondary" style="padding: 0.2rem 0.5rem; font-size: 0.75rem;" onclick="prefillTxn('${acc.accountId}')">Select</button>
                </div>
            `;
            grid.appendChild(card);

            // Populate form selects
            const opt1 = new Option(`${acc.accountId} (${acc.customerName} - ₹${acc.balance})`, acc.accountId);
            const opt2 = new Option(`${acc.accountId} (${acc.customerName} - ₹${acc.balance})`, acc.accountId);
            srcSelect.add(opt1);
            destSelect.add(opt2);
        });

        // Set distinct defaults
        if (destSelect.options.length > 1) {
            destSelect.selectedIndex = 1;
        }
    } catch (err) {
        console.error("Error loading accounts:", err);
    }
}

function prefillTxn(accId) {
    switchTab("tab-execute");
    const srcSelect = document.getElementById("txn-source");
    srcSelect.value = accId;
}

async function resetAcc1001() {
    try {
        const res = await fetch("/api/accounts/ACC1001/reset-10k", { method: "POST" });
        if (res.ok) {
            await loadAccounts();
            alert("Account ACC1001 balance has been successfully reset to ₹10,000.00 for testing!");
        }
    } catch (err) {
        alert("Failed to reset balance: " + err);
    }
}

function toggleAccountInputs() {
    const type = document.getElementById("txn-type").value;
    const groupSrc = document.getElementById("group-source-acc");
    const groupDest = document.getElementById("group-dest-acc");

    if (type === "DEPOSIT") {
        groupSrc.style.display = "none";
        groupDest.style.display = "block";
    } else if (type === "WITHDRAWAL") {
        groupSrc.style.display = "block";
        groupDest.style.display = "none";
    } else {
        groupSrc.style.display = "block";
        groupDest.style.display = "block";
    }
}

// 2. Transaction Submit
async function handleTransactionSubmit(event) {
    event.preventDefault();

    const type = document.getElementById("txn-type").value;
    const txnId = document.getElementById("txn-id").value.trim();
    const amount = parseFloat(document.getElementById("txn-amount").value);
    const srcAcc = type !== "DEPOSIT" ? document.getElementById("txn-source").value : null;
    const destAcc = type !== "WITHDRAWAL" ? document.getElementById("txn-dest").value : null;

    const payload = {
        transactionId: txnId,
        sourceAccount: srcAcc,
        destinationAccount: destAcc,
        amount: amount,
        type: type
    };

    const banner = document.getElementById("txn-result-banner");
    const jsonOutput = document.getElementById("txn-response-json");

    try {
        const res = await fetch("/api/transactions", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        const data = await res.json();
        jsonOutput.textContent = JSON.stringify(data, null, 2);

        if (res.ok) {
            banner.style.display = "block";
            banner.style.background = "#dcfce7";
            banner.style.border = "1px solid #86efac";
            banner.style.color = "#15803d";
            banner.innerHTML = `<strong>✅ Transaction Succeeded!</strong><br>
                                Thread: <code>${data.threadName}</code> | Duration: ${data.durationMs}ms<br>
                                Remaining Source Balance: ₹${data.remainingSourceBalance ?? 'N/A'}`;
            generateRandomTxnId();
            loadAccounts();
        } else {
            banner.style.display = "block";
            banner.style.background = "#fee2e2";
            banner.style.border = "1px solid #fca5a5";
            banner.style.color = "#991b1b";
            banner.innerHTML = `<strong>❌ Transaction Rejected / Failed!</strong><br>
                                Code: <code>${data.errorCode}</code> | Category: ${data.errorCategory}<br>
                                Reason: ${data.message}`;
        }
    } catch (err) {
        banner.style.display = "block";
        banner.style.background = "#fee2e2";
        banner.style.color = "#991b1b";
        banner.innerHTML = `<strong>Network / System Error:</strong> ${err.message}`;
    }
}

// 3. Mandatory Concurrency Scenario
async function runConcurrencyScenario() {
    const btn = document.getElementById("btn-run-concurrency");
    const resultsContainer = document.getElementById("concurrency-results");
    const verdict = document.getElementById("concurrency-verdict");
    const cardsContainer = document.getElementById("thread-cards-container");

    btn.disabled = true;
    btn.textContent = "⏳ Running simultaneous threads with CountDownLatch...";

    try {
        const res = await fetch("/api/concurrency/run-mandatory-scenario", { method: "POST" });
        const data = await res.json();

        resultsContainer.style.display = "block";
        cardsContainer.innerHTML = "";

        if (data.invariantSatisfied) {
            verdict.style.background = "#dcfce7";
            verdict.style.color = "#166534";
            verdict.style.border = "1px solid #86efac";
            verdict.innerHTML = `🏆 <strong>INVARIANT SATISFIED:</strong> ${data.summary}<br>
                                 Opening: ₹${data.openingBalance} ➔ Final Balance: ₹${data.finalBalance}`;
        } else {
            verdict.style.background = "#fee2e2";
            verdict.style.color = "#991b1b";
            verdict.innerHTML = `❌ <strong>INVARIANT VIOLATION:</strong> ${data.summary}`;
        }

        data.threadDetails.forEach(thread => {
            const isWinner = thread.status === "SUCCESS";
            const card = document.createElement("div");
            card.className = `thread-card ${isWinner ? 'winner' : 'loser'}`;
            card.innerHTML = `
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:0.5rem;">
                    <strong>${isWinner ? '✅ WINNING THREAD' : '⛔ REJECTED THREAD'}</strong>
                    <span class="tag ${isWinner ? 'tag-success' : 'tag-danger'}">${thread.status}</span>
                </div>
                <div><strong>Thread Name:</strong> <code>${thread.threadName}</code></div>
                <div><strong>Txn ID:</strong> <code>${thread.transactionId}</code></div>
                <div><strong>Requested Amount:</strong> ₹${thread.requestedAmount}</div>
                <div><strong>Duration:</strong> ${thread.durationMs} ms</div>
                <div style="margin-top:0.5rem; font-size:0.85rem; color:#334155;"><strong>Outcome Detail:</strong> ${thread.message}</div>
            `;
            cardsContainer.appendChild(card);
        });

        loadAccounts();
    } catch (err) {
        alert("Failed to execute concurrency test: " + err);
    } finally {
        btn.disabled = false;
        btn.textContent = "⚡ Fire Simultaneous Concurrency Test Now";
    }
}

// 4. Benchmark Suite
async function runBenchmarkSuite() {
    const count = document.getElementById("bm-txn-count").value;
    const btn = document.getElementById("btn-run-bm");
    const spinner = document.getElementById("bm-spinner");
    const container = document.getElementById("benchmark-results-container");
    const tbody = document.querySelector("#benchmark-table tbody");
    const analysisText = document.getElementById("bm-analysis-text");

    btn.disabled = true;
    spinner.style.display = "inline";

    try {
        const res = await fetch(`/api/benchmark/run?transactions=${count}`, { method: "POST" });
        const data = await res.json();

        container.style.display = "block";
        tbody.innerHTML = "";

        data.threadMetrics.forEach(metric => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td><strong>${metric.workerThreads} Threads</strong></td>
                <td>${metric.totalTransactions.toLocaleString()}</td>
                <td style="color:#15803d; font-weight:600;">${metric.successfulTransactions.toLocaleString()}</td>
                <td style="color:#b91c1c; font-weight:600;">${metric.failedTransactions.toLocaleString()}</td>
                <td>${metric.totalTimeMs.toLocaleString()} ms</td>
                <td><strong style="color:var(--accent); font-size:1.05rem;">${metric.tps} TPS</strong></td>
                <td style="font-size:0.85rem; color:#475569;">${metric.observedBottleneck}</td>
            `;
            tbody.appendChild(tr);
        });

        analysisText.textContent = data.bottleneckAnalysis;
        loadAccounts();
    } catch (err) {
        alert("Benchmark execution error: " + err);
    } finally {
        btn.disabled = false;
        spinner.style.display = "none";
    }
}

// 5. SQL Reports
async function loadAllReports() {
    try {
        const res = await fetch("/api/reports");
        currentReports = await res.json();
        if (currentReports.length > 0) {
            showReport(1);
        }
    } catch (err) {
        console.error("Error loading reports:", err);
    }
}

function showReport(reportId) {
    const report = currentReports.find(r => r.reportId === reportId);
    if (!report) return;

    document.getElementById("report-title").textContent = report.reportTitle;
    document.getElementById("report-sql").textContent = report.sqlQuery;
    document.getElementById("report-explain").textContent = report.explainPlan;

    const thead = document.getElementById("report-thead");
    const tbody = document.getElementById("report-tbody");
    thead.innerHTML = "";
    tbody.innerHTML = "";

    const headerRow = document.createElement("tr");
    report.columns.forEach(col => {
        const th = document.createElement("th");
        th.textContent = col.toUpperCase().replace(/_/g, " ");
        headerRow.appendChild(th);
    });
    thead.appendChild(headerRow);

    report.rows.forEach(row => {
        const tr = document.createElement("tr");
        report.columns.forEach(col => {
            const td = document.createElement("td");
            td.textContent = row[col] !== null ? row[col] : "NULL";
            tr.appendChild(td);
        });
        tbody.appendChild(tr);
    });
}

// 6. Audit & Failure Logs
async function loadAuditAndFailedLogs() {
    try {
        const [auditRes, failedRes] = await Promise.all([
            fetch("/api/transactions/audit?limit=25"),
            fetch("/api/transactions/failed?limit=25")
        ]);

        const audits = await auditRes.json();
        const fails = await failedRes.json();

        const auditTbody = document.querySelector("#audit-table tbody");
        auditTbody.innerHTML = "";
        audits.forEach(a => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td><code>${a.transactionId}</code></td>
                <td>${a.operationType}</td>
                <td><code>${a.threadName}</code></td>
                <td>${a.durationMs}ms</td>
                <td><span class="tag ${a.status === 'SUCCESS' ? 'tag-success' : 'tag-danger'}">${a.status}</span></td>
            `;
            auditTbody.appendChild(tr);
        });

        const failedTbody = document.querySelector("#failed-table tbody");
        failedTbody.innerHTML = "";
        fails.forEach(f => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td><code>${f.transactionId}</code></td>
                <td><span class="tag tag-danger">${f.errorCategory}</span></td>
                <td><code>${f.errorCode}</code></td>
                <td style="font-size:0.85rem;">${f.failureReason}</td>
            `;
            failedTbody.appendChild(tr);
        });
    } catch (err) {
        console.error("Error loading logs:", err);
    }
}

// 7. AI Banking Assistant Chatbot
function toggleChatWindow() {
    const win = document.getElementById("chatbot-window");
    win.classList.toggle("hidden");
    if (!win.classList.contains("hidden")) {
        document.getElementById("chat-input").focus();
    }
}

function sendQuickChat(text) {
    document.getElementById("chat-input").value = text;
    sendChatMessage();
}

async function sendChatMessage() {
    const input = document.getElementById("chat-input");
    const text = input.value.trim();
    if (!text) return;

    appendChatMessage("user", text);
    input.value = "";

    try {
        const res = await fetch("/api/chatbot/query", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ query: text })
        });
        const data = await res.json();
        appendChatMessage("bot", data.answer, data.suggestedQuestions);
    } catch (err) {
        appendChatMessage("bot", "Sorry, I encountered an issue connecting to the core engine: " + err.message);
    }
}

function appendChatMessage(sender, text, suggestions = []) {
    const messages = document.getElementById("chat-messages");
    const div = document.createElement("div");
    div.className = `chat-msg ${sender}`;

    // Simple markdown conversion for bold and code
    let formatted = text
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/`(.*?)`/g, '<code>$1</code>')
        .replace(/\n/g, '<br>');

    div.innerHTML = formatted;

    if (suggestions && suggestions.length > 0) {
        const suggContainer = document.createElement("div");
        suggContainer.className = "chat-suggestions";
        suggestions.forEach(q => {
            const btn = document.createElement("button");
            btn.className = "suggestion-pill";
            btn.textContent = q;
            btn.onclick = () => sendQuickChat(q);
            suggContainer.appendChild(btn);
        });
        div.appendChild(suggContainer);
    }

    messages.appendChild(div);
    messages.scrollTop = messages.scrollHeight;
}

// 8. AI Innovations Lab Handlers

async function runAiFraudAssessment() {
    const acc = document.getElementById("ai-fraud-acc").value.trim();
    const amount = parseFloat(document.getElementById("ai-fraud-amount").value);
    const type = document.getElementById("ai-fraud-type").value;
    const scoreDisplay = document.getElementById("ai-fraud-score-display");
    const factorsDisplay = document.getElementById("ai-fraud-factors");

    const payload = {
        transactionId: "TXN-FRAUD-TEST-" + Date.now().toString().slice(-4),
        sourceAccount: acc,
        destinationAccount: null,
        amount: amount,
        type: type
    };

    try {
        const res = await fetch("/api/ai/fraud/assess", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        const data = await res.json();

        scoreDisplay.innerHTML = `Risk Score: <strong style="color: ${data.fraudFlagged ? '#ef4444' : (data.riskScore >= 0.5 ? '#f59e0b' : '#10b981')}">${data.riskScore} / 1.00</strong> [${data.riskLevel}]`;
        
        let factorsHtml = `<strong>Status:</strong> ${data.fraudFlagged ? '⛔ FLAGGED AS FRAUD (Transaction Intercepted)' : '✅ APPROVED (Low Risk)'}<br><br><strong>Detected Factors:</strong><ul style="margin-left:1.2rem; margin-top:0.3rem;">`;
        data.riskFactors.forEach(f => {
            factorsHtml += `<li>${f}</li>`;
        });
        factorsHtml += `</ul>`;

        factorsDisplay.innerHTML = factorsHtml;
    } catch (err) {
        alert("Failed to run AI fraud check: " + err);
    }
}

async function runContentionComparison() {
    const acc = document.getElementById("ai-contention-acc").value.trim();
    const count = document.getElementById("ai-contention-count").value;
    const btn = document.getElementById("btn-contention-run");
    const spinner = document.getElementById("ai-contention-spinner");
    const container = document.getElementById("ai-contention-results");
    const summary = document.getElementById("ai-contention-summary");
    const stdStats = document.getElementById("ai-standard-stats");
    const batchedStats = document.getElementById("ai-batched-stats");

    btn.disabled = true;
    spinner.style.display = "inline";

    try {
        const res = await fetch(`/api/ai/contention/batch-run?account=${acc}&count=${count}`, { method: "POST" });
        const data = await res.json();

        container.style.display = "block";
        summary.textContent = `🚀 Contention Speedup Achieved: ${data.speedupFactor}x Faster!`;

        stdStats.innerHTML = `
            Time: <strong>${data.standardProcessingTimeMs} ms</strong><br>
            Throughput: <strong>${data.standardTps} TPS</strong><br>
            Bottleneck: Row-level lock contention on hot account (${data.totalRequestsInBatch} individual roundtrips)
        `;

        batchedStats.innerHTML = `
            Time: <strong>${data.aiBatchedProcessingTimeMs} ms</strong><br>
            Throughput: <strong style="color:#15803d; font-size:1.1rem;">${data.aiBatchedTps} TPS</strong><br>
            Optimization: Collapsed into 1 atomic batch balance update of ₹${data.totalBatchedAmount}
        `;

        loadAccounts();
    } catch (err) {
        alert("Failed to run contention comparison: " + err);
    } finally {
        btn.disabled = false;
        spinner.style.display = "none";
    }
}

async function runAiSqlOptimizer() {
    const tbody = document.getElementById("ai-sql-tbody");
    tbody.innerHTML = `<tr><td colspan="5" style="text-align:center;">⏳ Diagnosing SQL query plans...</td></tr>`;

    try {
        const res = await fetch("/api/ai/sql-optimizer/diagnose");
        const data = await res.json();

        tbody.innerHTML = "";
        data.diagnoses.forEach(diag => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td><strong>${diag.queryTitle}</strong></td>
                <td><span class="tag tag-success">${diag.scanTypeDetected}</span></td>
                <td><strong>${diag.estimatedScanCost}</strong></td>
                <td style="font-size:0.85rem; color:#334155;">${diag.aiDiagnosis}</td>
                <td style="font-size:0.85rem; color:#0369a1;"><strong>${diag.recommendedAction}</strong></td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="5" style="color:red;">Error diagnosing queries: ${err.message}</td></tr>`;
    }
}
