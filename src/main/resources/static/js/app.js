// WebTestNReport Frontend Application

// State
let currentTab = 'dashboard';
let rules = [];
let runs = [];
let tickets = [];

// DOM Elements
const navButtons = document.querySelectorAll('.nav-btn');
const tabPanes = document.querySelectorAll('.tab-pane');
const pageTitle = document.getElementById('page-title');
const currentTimeSpan = document.getElementById('current-time');

// Document Ready
document.addEventListener('DOMContentLoaded', () => {
    // Initial clock setup
    updateClock();
    setInterval(updateClock, 1000);

    // Tab Navigation setup
    navButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            const tabId = btn.getAttribute('data-tab');
            switchTab(tabId);
        });
    });

    // Snippets Dropdown Setup
    const btnShowSnippets = document.getElementById('btn-show-snippets');
    const snippetsMenu = document.getElementById('snippets-menu');
    btnShowSnippets.addEventListener('click', (e) => {
        e.stopPropagation();
        snippetsMenu.classList.toggle('active');
    });
    
    document.addEventListener('click', () => {
        snippetsMenu.classList.remove('active');
    });

    // Snippet Clicks
    const scriptTextarea = document.getElementById('rule-script');
    snippetsMenu.querySelectorAll('a').forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            const snippetText = link.getAttribute('data-snippet');
            insertAtCursor(scriptTextarea, snippetText + '\n');
            scriptTextarea.focus();
        });
    });

    // Rule Modal handlers
    const ruleModal = document.getElementById('rule-modal');
    const btnAddRule = document.getElementById('btn-add-rule');
    const btnCloseModal = document.getElementById('btn-close-modal');
    const btnCancelModal = document.getElementById('btn-cancel-modal');
    const ruleForm = document.getElementById('rule-form');

    btnAddRule.addEventListener('click', () => openRuleModal());
    btnCloseModal.addEventListener('click', closeRuleModal);
    btnCancelModal.addEventListener('click', closeRuleModal);
    ruleForm.addEventListener('submit', saveRule);

    // Manual test trigger in rule modal
    const btnTestModal = document.getElementById('btn-test-modal');
    btnTestModal.addEventListener('click', runManualTestFromModal);

    // Close Run detail modal
    document.getElementById('btn-close-run-modal').addEventListener('click', () => closeModal('run-modal'));
    document.getElementById('btn-close-run-details').addEventListener('click', () => closeModal('run-modal'));

    // Quick run active rules from header
    document.getElementById('quick-run-all').addEventListener('click', triggerRunAll);

    // Initial load
    refreshData();
});

// Update datetime clock in header
function updateClock() {
    const now = new Date();
    currentTimeSpan.textContent = now.toLocaleDateString() + ' ' + now.toLocaleTimeString();
}

// Switch between navigation tabs
function switchTab(tabId) {
    currentTab = tabId;
    
    // Update navigation active states
    navButtons.forEach(btn => {
        if (btn.getAttribute('data-tab') === tabId) {
            btn.classList.add('active');
        } else {
            btn.classList.remove('active');
        }
    });

    // Update active content panels
    tabPanes.forEach(pane => {
        if (pane.id === `tab-${tabId}`) {
            pane.classList.add('active');
        } else {
            pane.classList.remove('active');
        }
    });

    // Header Title
    pageTitle.textContent = tabId.charAt(0).toUpperCase() + tabId.slice(1).replace('-', ' ');

    // Refresh specific tab data
    refreshData();
}

// Global Refresh
async function refreshData() {
    try {
        await Promise.all([
            loadRules(),
            loadRuns(),
            loadTickets()
        ]);
        
        updateStats();
        renderDashboard();
        renderRules();
        renderRuns();
        renderTickets();
    } catch (err) {
        console.error('Error refreshing data:', err);
    }
}

// Loaders
async function loadRules() {
    const res = await fetch('/api/rules');
    rules = await res.json();
}

async function loadRuns() {
    const res = await fetch('/api/runs');
    runs = await res.json();
}

async function loadTickets() {
    const res = await fetch('/api/tickets');
    tickets = await res.json();
}

// Calculate Dashboard Stats
function updateStats() {
    const totalRuns = runs.length;
    const activeRules = rules.filter(r => r.active).length;
    const openTickets = tickets.filter(t => t.status !== 'RESOLVED').length;

    // Success Rate
    let successRate = '100%';
    if (totalRuns > 0) {
        const successes = runs.filter(r => r.status === 'SUCCESS').length;
        successRate = Math.round((successes / totalRuns) * 100) + '%';
    }

    document.getElementById('stat-success-rate').textContent = successRate;
    document.getElementById('stat-active-rules').textContent = activeRules;
    document.getElementById('stat-open-tickets').textContent = openTickets;
    document.getElementById('stat-total-runs').textContent = totalRuns;

    // Badge indicator on tickets side menu
    const badge = document.getElementById('ticket-badge');
    if (openTickets > 0) {
        badge.textContent = openTickets;
        badge.classList.remove('hidden');
    } else {
        badge.classList.add('hidden');
    }
}

// Render Dashboard
function renderDashboard() {
    // Render Recent Tickets table
    const ticketsTbody = document.getElementById('recent-tickets-tbody');
    const activeIncidents = tickets.filter(t => t.status !== 'RESOLVED').slice(0, 5);

    if (activeIncidents.length === 0) {
        ticketsTbody.innerHTML = `<tr><td colspan="5" class="text-center">No active incidents! All systems green.</td></tr>`;
    } else {
        ticketsTbody.innerHTML = activeIncidents.map(t => `
            <tr>
                <td><strong>#${t.id}</strong> - ${escapeHtml(t.title)}</td>
                <td>${escapeHtml(t.ruleName)}</td>
                <td><span class="severity-tag sev-${t.severity.toLowerCase()}">${t.severity}</span></td>
                <td><span class="status-tag ${t.status.toLowerCase().replace('_', '')}">${t.status.replace('_', ' ')}</span></td>
                <td>${formatDate(t.createdAt)}</td>
            </tr>
        `).join('');
    }

    // Render Recent Runs table
    const runsTbody = document.getElementById('recent-runs-tbody');
    const recentRuns = runs.slice(0, 5);

    if (recentRuns.length === 0) {
        runsTbody.innerHTML = `<tr><td colspan="5" class="text-center">No runs recorded yet.</td></tr>`;
    } else {
        runsTbody.innerHTML = recentRuns.map(r => `
            <tr>
                <td><span class="status-tag ${r.status.toLowerCase()}">${r.status}</span></td>
                <td><strong>${escapeHtml(r.ruleName)}</strong></td>
                <td>${r.durationMs} ms</td>
                <td>${formatDate(r.startedAt)}</td>
                <td>
                    <button class="btn btn-secondary btn-xs" onclick="viewRunDetails(${r.id})">
                        <i class="fa-solid fa-eye"></i> View Log
                    </button>
                </td>
            </tr>
        `).join('');
    }
}

// Render Rules
function renderRules() {
    const list = document.getElementById('rules-list');
    if (rules.length === 0) {
        list.innerHTML = `<div class="card col-12 text-center" style="grid-column: 1 / -1; padding: 40px;"><p>No test rules created yet. Click "Create New Rule" to get started.</p></div>`;
        return;
    }

    list.innerHTML = rules.map(rule => `
        <div class="card rule-card">
            <div>
                <div class="rule-meta">
                    <span class="status-tag ${rule.active ? 'success' : 'secondary'}" style="background-color: ${rule.active ? 'var(--success-bg)' : 'var(--secondary)'}; color: ${rule.active ? 'var(--success)' : 'var(--text-secondary)'}">
                        ${rule.active ? 'Active Schedule' : 'Inactive'}
                    </span>
                    <span class="datetime">ID: #${rule.id}</span>
                </div>
                <h4 class="rule-name">${escapeHtml(rule.name)}</h4>
                <p class="rule-desc">${escapeHtml(rule.description || 'No description provided.')}</p>
                <div class="rule-schedule">
                    <i class="fa-regular fa-clock"></i> Checks every <strong>${rule.intervalMinutes} min</strong>
                </div>
            </div>
            <div class="rule-actions">
                <button class="btn btn-warning btn-xs" onclick="runManualTest(${rule.id})">
                    <i class="fa-solid fa-play"></i> Run
                </button>
                <button class="btn btn-secondary btn-xs" onclick="openRuleModal(${rule.id})">
                    <i class="fa-solid fa-pen-to-square"></i> Edit
                </button>
                <button class="btn btn-danger btn-xs" onclick="deleteRule(${rule.id})">
                    <i class="fa-solid fa-trash-can"></i> Delete
                </button>
            </div>
        </div>
    `).join('');
}

// Render All Runs Table
function renderRuns() {
    const tbody = document.getElementById('all-runs-tbody');
    if (runs.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center">No execution runs recorded yet.</td></tr>`;
        return;
    }

    tbody.innerHTML = runs.map(r => `
        <tr>
            <td><span class="status-tag ${r.status.toLowerCase()}">${r.status}</span></td>
            <td><strong>${escapeHtml(r.ruleName)}</strong></td>
            <td>${r.durationMs} ms</td>
            <td>${formatDate(r.startedAt)}</td>
            <td style="max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                ${r.errorMessage ? escapeHtml(r.errorMessage) : '<span class="text-muted">None</span>'}
            </td>
            <td>
                <button class="btn btn-secondary btn-xs" onclick="viewRunDetails(${r.id})">
                    <i class="fa-solid fa-eye"></i> View Log
                </button>
            </td>
        </tr>
    `).join('');
}

// Render Kanban Tickets Board
function renderTickets() {
    const colOpen = document.getElementById('cards-open');
    const colProgress = document.getElementById('cards-progress');
    const colResolved = document.getElementById('cards-resolved');

    const openList = tickets.filter(t => t.status === 'OPEN');
    const progressList = tickets.filter(t => t.status === 'IN_PROGRESS');
    const resolvedList = tickets.filter(t => t.status === 'RESOLVED');

    document.getElementById('count-open').textContent = openList.length;
    document.getElementById('count-progress').textContent = progressList.length;
    document.getElementById('count-resolved').textContent = resolvedList.length;

    const buildCardHtml = (t) => `
        <div class="ticket-card">
            <div class="rule-meta">
                <span class="severity-tag sev-${t.severity.toLowerCase()}">${t.severity} Severity</span>
                <span class="text-muted">Ticket #${t.id}</span>
            </div>
            <h4>${escapeHtml(t.title)}</h4>
            <p>${escapeHtml(t.description)}</p>
            <div class="ticket-meta">
                <span>Rule: <strong>${escapeHtml(t.ruleName)}</strong></span>
                <span>${formatDate(t.updatedAt)}</span>
            </div>
            <div class="ticket-actions">
                ${t.status === 'OPEN' ? `
                    <button class="btn btn-secondary btn-xs col-12" onclick="updateTicketStatus(${t.id}, 'IN_PROGRESS')">
                        <i class="fa-solid fa-spinner"></i> Start Investigation
                    </button>
                ` : ''}
                ${t.status === 'IN_PROGRESS' ? `
                    <button class="btn btn-primary btn-xs col-6" onclick="updateTicketStatus(${t.id}, 'RESOLVED')">
                        <i class="fa-solid fa-check"></i> Resolve
                    </button>
                    <button class="btn btn-secondary btn-xs col-6" onclick="updateTicketStatus(${t.id}, 'OPEN')">
                        <i class="fa-solid fa-times"></i> Reopen
                    </button>
                ` : ''}
                ${t.status === 'RESOLVED' ? `
                    <button class="btn btn-secondary btn-xs col-12" onclick="updateTicketStatus(${t.id}, 'IN_PROGRESS')">
                        <i class="fa-solid fa-rotate-left"></i> Reopen to Progress
                    </button>
                ` : ''}
            </div>
        </div>
    `;

    colOpen.innerHTML = openList.length === 0 ? '<p class="text-center text-muted" style="padding: 20px 0;">No open issues</p>' : openList.map(buildCardHtml).join('');
    colProgress.innerHTML = progressList.length === 0 ? '<p class="text-center text-muted" style="padding: 20px 0;">No active tasks</p>' : progressList.map(buildCardHtml).join('');
    colResolved.innerHTML = resolvedList.length === 0 ? '<p class="text-center text-muted" style="padding: 20px 0;">No resolved tickets</p>' : resolvedList.map(buildCardHtml).join('');
}

// Ticket Action API Trigger
async function updateTicketStatus(id, newStatus) {
    try {
        const res = await fetch(`/api/tickets/${id}/status?status=${newStatus}`, {
            method: 'PUT'
        });
        if (res.ok) {
            refreshData();
        } else {
            alert('Failed to update ticket status');
        }
    } catch (err) {
        console.error(err);
    }
}

// Modal Form handling
function openRuleModal(id = null) {
    const title = document.getElementById('modal-title');
    const ruleId = document.getElementById('rule-id');
    const name = document.getElementById('rule-name');
    const desc = document.getElementById('rule-description');
    const interval = document.getElementById('rule-interval');
    const active = document.getElementById('rule-active');
    const script = document.getElementById('rule-script');

    if (id) {
        // Edit Mode
        const rule = rules.find(r => r.id === id);
        title.textContent = 'Edit Test Rule';
        ruleId.value = rule.id;
        name.value = rule.name;
        desc.value = rule.description || '';
        interval.value = rule.intervalMinutes;
        active.checked = rule.active;
        script.value = rule.script || '';
    } else {
        // Create Mode
        title.textContent = 'Create Test Rule';
        ruleId.value = '';
        name.value = '';
        desc.value = '';
        interval.value = '5';
        active.checked = true;
        script.value = '# Enter browser testing actions line-by-line\ngoto https://example.com\nassert-text h1 = Example Domain';
    }

    document.getElementById('rule-modal').classList.add('active');
}

function closeRuleModal() {
    document.getElementById('rule-modal').classList.remove('active');
}

async function saveRule(e) {
    e.preventDefault();
    const id = document.getElementById('rule-id').value;
    const payload = {
        name: document.getElementById('rule-name').value,
        description: document.getElementById('rule-description').value,
        intervalMinutes: parseInt(document.getElementById('rule-interval').value),
        active: document.getElementById('rule-active').checked,
        script: document.getElementById('rule-script').value
    };

    const url = id ? `/api/rules/${id}` : '/api/rules';
    const method = id ? 'PUT' : 'POST';

    try {
        const res = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            closeRuleModal();
            refreshData();
        } else {
            alert('Error saving rule');
        }
    } catch (err) {
        console.error(err);
    }
}

// Delete Rule
async function deleteRule(id) {
    if (!confirm('Are you sure you want to delete this test rule? All associated history will remain but the rule scheduler will stop.')) {
        return;
    }
    try {
        const res = await fetch(`/api/rules/${id}`, {
            method: 'DELETE'
        });
        if (res.ok) {
            refreshData();
        } else {
            alert('Failed to delete rule');
        }
    } catch (err) {
        console.error(err);
    }
}

// Trigger all active rules to run manually in background
async function triggerRunAll() {
    const active = rules.filter(r => r.active);
    if (active.length === 0) {
        alert('No active test rules found to execute.');
        return;
    }

    const btn = document.getElementById('quick-run-all');
    btn.disabled = true;
    btn.innerHTML = `<i class="fa-solid fa-spinner fa-spin"></i> Triggering...`;

    try {
        for (const rule of active) {
            fetch(`/api/rules/${rule.id}/run`, { method: 'POST' });
        }
        alert(`Triggered executions for ${active.length} active rules in the background. Please wait a few seconds and refresh.`);
        setTimeout(refreshData, 3000);
    } catch (e) {
        console.error(e);
    } finally {
        btn.disabled = false;
        btn.innerHTML = `<i class="fa-solid fa-play"></i> Run Active Rules`;
    }
}

// Manual Test Run (from Rules List Page)
async function runManualTest(id) {
    const terminal = document.getElementById('live-terminal-modal');
    const logEl = document.getElementById('live-terminal-log');
    const spinner = document.getElementById('live-terminal-spinner');
    const doneBtn = document.getElementById('btn-close-live-terminal');

    // Reset and open modal
    logEl.textContent = 'Initializing connection to headless browser engine...\n';
    spinner.classList.remove('hidden');
    doneBtn.classList.add('hidden');
    terminal.classList.add('active');

    try {
        const res = await fetch(`/api/rules/${id}/run`, {
            method: 'POST'
        });
        const runResult = await res.json();

        // Populate log
        logEl.textContent = runResult.log;
        spinner.classList.add('hidden');
        doneBtn.classList.remove('hidden');
        doneBtn.onclick = () => {
            terminal.classList.remove('active');
            refreshData();
        };
    } catch (err) {
        logEl.textContent += `\n[CRITICAL ERROR] Failed to connect to server: ${err.message}`;
        spinner.classList.add('hidden');
        doneBtn.classList.remove('hidden');
        doneBtn.onclick = () => terminal.classList.remove('active');
    }
}

// Manual Test run trigger from inside Create/Edit Modal
async function runManualTestFromModal() {
    // 1. Get temporary unsaved script content
    const scriptVal = document.getElementById('rule-script').value;
    const nameVal = document.getElementById('rule-name').value || 'Unsaved Rule';
    
    if (!scriptVal.trim()) {
        alert('Please write a script first in the Scratchpad Editor.');
        return;
    }

    const terminal = document.getElementById('live-terminal-modal');
    const logEl = document.getElementById('live-terminal-log');
    const spinner = document.getElementById('live-terminal-spinner');
    const doneBtn = document.getElementById('btn-close-live-terminal');

    // Reset and open modal
    logEl.textContent = 'Creating temp context...\nConnecting to Chromium...\n';
    spinner.classList.remove('hidden');
    doneBtn.classList.add('hidden');
    terminal.classList.add('active');

    // We make a dynamic run by creating/updating a mock/temp rule or saving current state first
    // To make it easy and not pollute, let's create a temporary rule payload on backend?
    // Wait, let's just save the rule first dynamically, or if they don't want to save, let's save it.
    // Actually, saving it and then running it is extremely robust! Let's explain to the user and save it first.
    // Wait, the simplest way is to save it, get the ID, and then run it.
    // Let's do that: save rule first, then trigger run!
    
    // Save rule
    const id = document.getElementById('rule-id').value;
    const payload = {
        name: document.getElementById('rule-name').value || 'Draft - ' + new Date().toLocaleTimeString(),
        description: document.getElementById('rule-description').value,
        intervalMinutes: parseInt(document.getElementById('rule-interval').value) || 5,
        active: document.getElementById('rule-active').checked,
        script: scriptVal
    };

    const url = id ? `/api/rules/${id}` : '/api/rules';
    const method = id ? 'PUT' : 'POST';

    try {
        const saveRes = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (saveRes.ok) {
            const savedRule = await saveRes.json();
            // Update modal field with saved ID
            document.getElementById('rule-id').value = savedRule.id;
            
            // Now run it!
            logEl.textContent += `Rule saved successfully with ID #${savedRule.id}.\nStarting execution...\n`;
            const runRes = await fetch(`/api/rules/${savedRule.id}/run`, { method: 'POST' });
            const runResult = await runRes.json();
            
            logEl.textContent = runResult.log;
            spinner.classList.add('hidden');
            doneBtn.classList.remove('hidden');
            doneBtn.onclick = () => {
                terminal.classList.remove('active');
                refreshData();
            };
        } else {
            logEl.textContent += `[ERROR] Failed to save the rule before running.`;
            spinner.classList.add('hidden');
            doneBtn.classList.remove('hidden');
            doneBtn.onclick = () => terminal.classList.remove('active');
        }
    } catch (err) {
        logEl.textContent += `\n[CRITICAL ERROR] ${err.message}`;
        spinner.classList.add('hidden');
        doneBtn.classList.remove('hidden');
        doneBtn.onclick = () => terminal.classList.remove('active');
    }
}

// View Test Run Details (Logs & Screenshot)
function viewRunDetails(runId) {
    const run = runs.find(r => r.id === runId);
    if (!run) return;

    document.getElementById('run-detail-title').textContent = run.ruleName;
    document.getElementById('run-detail-status').textContent = run.status;
    
    // Apply styling based on status
    const statusEl = document.getElementById('run-detail-status');
    statusEl.className = `status-tag ${run.status.toLowerCase()}`;

    document.getElementById('run-detail-duration').textContent = `${run.durationMs} ms`;
    document.getElementById('run-detail-time').textContent = formatDate(run.startedAt);
    document.getElementById('run-detail-log').textContent = run.log;

    const screenshotContainer = document.getElementById('screenshot-container');
    const screenshotImg = document.getElementById('run-detail-screenshot');

    if (run.screenshotPath) {
        screenshotImg.src = run.screenshotPath;
        screenshotContainer.classList.remove('hidden');
    } else {
        screenshotContainer.classList.add('hidden');
    }

    // Render E2E stages pipeline if available
    try {
        if (run.stagesJson) {
            const stages = JSON.parse(run.stagesJson);
            drawPipeline(stages);
        } else {
            document.getElementById('run-pipeline-container').classList.add('hidden');
        }
    } catch (e) {
        console.error('Error rendering stages JSON:', e);
        document.getElementById('run-pipeline-container').classList.add('hidden');
    }

    document.getElementById('run-modal').classList.add('active');
}

function drawPipeline(stages) {
    const container = document.getElementById('run-pipeline-container');
    if (!stages || !Array.isArray(stages) || stages.length === 0) {
        container.classList.add('hidden');
        return;
    }
    
    container.classList.remove('hidden');
    
    let html = '<div class="pipeline-stepper">';
    
    stages.forEach((stage, idx) => {
        let iconClass = 'fa-regular fa-circle';
        if (stage.status === 'SUCCESS') iconClass = 'fa-solid fa-circle-check';
        else if (stage.status === 'FAILED') iconClass = 'fa-solid fa-circle-xmark';
        else if (stage.status === 'RUNNING') iconClass = 'fa-solid fa-circle-notch fa-spin';
        else if (stage.status === 'SKIPPED') iconClass = 'fa-solid fa-ban';
        
        html += `
            <div class="pipeline-step" onclick="scrollToStage('${escapeJsString(stage.name)}')" title="Click to view logs for this stage">
                <div class="pipeline-node ${stage.status.toLowerCase()}">
                    <i class="${iconClass}"></i>
                </div>
                <div class="pipeline-label">${escapeHtml(stage.name)}</div>
                ${stage.durationMs > 0 ? `<div style="font-size: 9px; color: var(--text-muted); margin-top: 2px;">${stage.durationMs} ms</div>` : ''}
            </div>
        `;
        
        if (idx < stages.length - 1) {
            let connActive = (stage.status === 'SUCCESS');
            html += `<div class="pipeline-connector ${connActive ? 'active' : ''}"></div>`;
        }
    });
    
    html += '</div>';
    container.innerHTML = html;
}

function scrollToStage(stageName) {
    const logEl = document.getElementById('run-detail-log');
    if (!logEl) return;
    
    const text = logEl.textContent;
    const searchStr = `=== Stage: ${stageName} ===`;
    const index = text.indexOf(searchStr);
    
    if (index !== -1) {
        const lines = text.substring(0, index).split('\n');
        const lineNum = lines.length;
        const lineHeight = 18; // approx px per line in terminal pre block
        logEl.scrollTop = (lineNum - 1) * lineHeight;
    }
}

function escapeJsString(str) {
    return str.replace(/'/g, "\\'").replace(/"/g, '\\"');
}

// Helper: close modal by id
function closeModal(id) {
    document.getElementById(id).classList.remove('active');
}

// Helper: insert code at cursor in textarea
function insertAtCursor(myField, myValue) {
    if (myField.selectionStart || myField.selectionStart === 0) {
        const startPos = myField.selectionStart;
        const endPos = myField.selectionEnd;
        myField.value = myField.value.substring(0, startPos)
            + myValue
            + myField.value.substring(endPos, myField.value.length);
        myField.selectionStart = startPos + myValue.length;
        myField.selectionEnd = startPos + myValue.length;
    } else {
        myField.value += myValue;
    }
}

// Helper: format ISO dates nicely
function formatDate(isoString) {
    if (!isoString) return 'N/A';
    try {
        const date = new Date(isoString);
        return date.toLocaleString();
    } catch (e) {
        return isoString;
    }
}

// Helper: escape HTML values
function escapeHtml(str) {
    if (!str) return '';
    return str
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
