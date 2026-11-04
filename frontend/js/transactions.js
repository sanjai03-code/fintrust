/**
 * FinTrust - Transaction Management Module
 */

const TransactionsModule = (() => {
    let transactions = [];
    let currentInspectionTx = null;

    async function loadTransactions() {
        const tbody = document.getElementById('transactions-tbody');
        if (tbody) tbody.innerHTML = '<tr><td colspan="8" class="text-center py-4 text-muted"><span class="spinner-border spinner-border-sm me-2"></span>Loading financial transactions...</td></tr>';

        const filterStatus = document.getElementById('filter-tx-status')?.value || '';
        const filterRisk = document.getElementById('filter-tx-risk')?.value || '';

        try {
            const params = {};
            if (filterStatus) params.status = filterStatus;
            if (filterRisk) params.risk_level = filterRisk;

            const res = await FinTrustAPI.transactions.list(params);
            if (res.success) {
                transactions = res.transactions || [];
                renderTable(transactions);
            }
        } catch (err) {
            console.error('[TransactionsModule] Error loading transactions:', err);
            if (tbody) tbody.innerHTML = `<tr><td colspan="8" class="text-center text-danger py-4">Failed to load transactions: ${err.message}</td></tr>`;
        }
    }

    function renderTable(txs) {
        const tbody = document.getElementById('transactions-tbody');
        if (!tbody) return;

        if (txs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center py-4 text-muted">No transactions matched your filter criteria.</td></tr>';
            return;
        }

        tbody.innerHTML = txs.map(tx => {
            const statusClass = `badge-ft-${tx.status.toLowerCase()}`;
            const riskClass = `badge-risk-${tx.risk_level.toLowerCase()}`;
            const formattedDate = tx.created_at ? new Date(tx.created_at).toLocaleString() : 'N/A';

            return `
                <tr>
                    <td class="mono-text fw-bold text-primary">${tx.transaction_ref}</td>
                    <td>
                        <div class="fw-semibold text-dark">${tx.organization_name || 'Corp Entity'}</div>
                        <div class="text-muted small">${tx.type} • Dest: ${tx.destination_country || 'USA'}</div>
                    </td>
                    <td class="fw-bold">${tx.currency} ${Number(tx.amount).toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                    <td class="mono-text small">
                        <div><i class="bi bi-box-arrow-up-right text-muted me-1"></i>${tx.source_account}</div>
                        <div><i class="bi bi-box-arrow-in-down-left text-muted me-1"></i>${tx.destination_account}</div>
                    </td>
                    <td><span class="badge-ft ${statusClass}">${tx.status.replace(/_/g, ' ')}</span></td>
                    <td>
                        <span class="badge-ft ${riskClass}">${tx.risk_level}</span>
                        <div class="small text-muted mt-1">Score: ${Number(tx.risk_score_cache || tx.total_score || 0).toFixed(1)}/100</div>
                    </td>
                    <td class="small text-muted">${formattedDate}</td>
                    <td>
                        <button class="btn btn-sm btn-ft-secondary py-1 px-2 btn-inspect-tx" data-id="${tx.id}">
                            <i class="bi bi-eye"></i> Details
                        </button>
                    </td>
                </tr>
            `;
        }).join('');

        tbody.querySelectorAll('.btn-inspect-tx').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = parseInt(btn.getAttribute('data-id'), 10);
                inspectTransaction(id);
            });
        });
    }

    async function inspectTransaction(txId) {
        const tx = transactions.find(t => t.id === txId);
        if (!tx) return;

        currentInspectionTx = tx;
        const modalEl = document.getElementById('transactionDetailModal');
        if (!modalEl) return;

        document.getElementById('td-ref').textContent = tx.transaction_ref;
        document.getElementById('td-org').textContent = tx.organization_name;
        document.getElementById('td-amount').textContent = `${tx.currency} ${Number(tx.amount).toLocaleString('en-US', { minimumFractionDigits: 2 })}`;
        document.getElementById('td-type').textContent = tx.type;
        document.getElementById('td-src').textContent = tx.source_account;
        document.getElementById('td-dst').textContent = `${tx.destination_account} (${tx.destination_country || 'USA'})`;
        document.getElementById('td-desc').textContent = tx.description || 'No description provided';
        document.getElementById('td-status').innerHTML = `<span class="badge-ft badge-ft-${tx.status.toLowerCase()}">${tx.status}</span>`;
        document.getElementById('td-risk').innerHTML = `<span class="badge-ft badge-risk-${tx.risk_level.toLowerCase()}">${tx.risk_level} (Score: ${Number(tx.risk_score_cache || tx.total_score || 0).toFixed(1)}/100)</span>`;

        // Render matched rules
        const rulesContainer = document.getElementById('td-rules-container');
        if (rulesContainer) {
            let rules = [];
            try {
                if (tx.rule_matches) rules = typeof tx.rule_matches === 'string' ? JSON.parse(tx.rule_matches) : tx.rule_matches;
            } catch (e) {}
            if (!rules.length) rules = ['Whitelisted / standard transaction pattern evaluated'];
            rulesContainer.innerHTML = rules.map(r => `<li class="small mb-1 text-secondary">${r}</li>`).join('');
        }

        const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
        modal.show();
    }

    async function updateStatus(txId, newStatus) {
        try {
            const res = await FinTrustAPI.transactions.updateStatus(txId, newStatus);
            if (res.success) {
                const modalEl = document.getElementById('transactionDetailModal');
                if (modalEl) bootstrap.Modal.getInstance(modalEl).hide();
                await loadTransactions();
                // Refresh dashboard if visible
                if (window.DashboardModule) DashboardModule.loadDashboard();
            }
        } catch (err) {
            alert('Failed to update status: ' + err.message);
        }
    }

    // Live Risk Calculator on Transaction Form
    async function updateLiveRiskPreview() {
        const amount = parseFloat(document.getElementById('tx-amount')?.value || 0);
        const country = document.getElementById('tx-country')?.value || 'USA';
        const type = document.getElementById('tx-type')?.value || 'TRANSFER';
        const src = document.getElementById('tx-source')?.value || '';
        const dst = document.getElementById('tx-dest')?.value || '';

        const scoreEl = document.getElementById('live-risk-score');
        const fillEl = document.getElementById('live-risk-fill');
        const labelEl = document.getElementById('live-risk-label');
        const rulesList = document.getElementById('live-risk-rules');

        if (!scoreEl) return;

        if (amount <= 0) {
            scoreEl.textContent = '0.0';
            fillEl.style.width = '0%';
            labelEl.textContent = 'Awaiting input';
            rulesList.innerHTML = '<li class="text-muted small">Enter transfer details to view rule calculation</li>';
            return;
        }

        try {
            const evalRes = await FinTrustAPI.risk.evaluate({
                amount,
                destination_country: country,
                type,
                source_account: src,
                destination_account: dst
            });

            if (evalRes.success && evalRes.risk_evaluation) {
                const ev = evalRes.risk_evaluation;
                const score = Number(ev.score).toFixed(1);
                scoreEl.textContent = score + '/100';
                fillEl.style.width = Math.min(100, Math.max(5, score)) + '%';

                if (ev.tier === 'CRITICAL') {
                    fillEl.style.backgroundColor = '#881337';
                    labelEl.innerHTML = '<span class="text-danger fw-bold"><i class="bi bi-shield-x"></i> CRITICAL RISK</span>';
                } else if (ev.tier === 'HIGH') {
                    fillEl.style.backgroundColor = '#EF4444';
                    labelEl.innerHTML = '<span class="text-danger fw-bold"><i class="bi bi-exclamation-octagon"></i> HIGH RISK</span>';
                } else if (ev.tier === 'MEDIUM') {
                    fillEl.style.backgroundColor = '#F59E0B';
                    labelEl.innerHTML = '<span class="text-warning fw-bold"><i class="bi bi-exclamation-triangle"></i> MEDIUM RISK</span>';
                } else {
                    fillEl.style.backgroundColor = '#10B981';
                    labelEl.innerHTML = '<span class="text-success fw-bold"><i class="bi bi-shield-check"></i> LOW RISK</span>';
                }

                rulesList.innerHTML = (ev.rules || []).map(r => `<li class="small mb-1">${r}</li>`).join('');
            }
        } catch (e) {
            // fallback
        }
    }

    function bindEvents() {
        // Filter changes
        document.getElementById('filter-tx-status')?.addEventListener('change', loadTransactions);
        document.getElementById('filter-tx-risk')?.addEventListener('change', loadTransactions);

        // Transaction Create Form
        const txForm = document.getElementById('createTransactionForm');
        if (txForm) {
            // Live preview listeners
            ['tx-amount', 'tx-country', 'tx-type', 'tx-source', 'tx-dest'].forEach(id => {
                document.getElementById(id)?.addEventListener('input', updateLiveRiskPreview);
                document.getElementById(id)?.addEventListener('change', updateLiveRiskPreview);
            });

            txForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                const btn = txForm.querySelector('button[type="submit"]');
                btn.disabled = true;

                const data = {
                    organization_id: parseInt(document.getElementById('tx-org')?.value || '1', 10),
                    type: document.getElementById('tx-type')?.value,
                    amount: parseFloat(document.getElementById('tx-amount')?.value),
                    currency: document.getElementById('tx-currency')?.value,
                    source_account: document.getElementById('tx-source')?.value,
                    destination_account: document.getElementById('tx-dest')?.value,
                    destination_country: document.getElementById('tx-country')?.value,
                    description: document.getElementById('tx-desc')?.value
                };

                try {
                    const res = await FinTrustAPI.transactions.create(data);
                    if (res.success) {
                        const modalEl = document.getElementById('createTransactionModal');
                        if (modalEl) bootstrap.Modal.getInstance(modalEl).hide();
                        txForm.reset();
                        updateLiveRiskPreview();
                        await loadTransactions();
                        if (window.DashboardModule) DashboardModule.loadDashboard();
                    }
                } catch (err) {
                    alert('Error creating transaction: ' + err.message);
                } finally {
                    btn.disabled = false;
                }
            });
        }

        // Action Buttons inside Inspection Modal
        document.getElementById('btn-approve-tx')?.addEventListener('click', () => {
            if (currentInspectionTx) updateStatus(currentInspectionTx.id, 'COMPLETED');
        });
        document.getElementById('btn-flag-tx')?.addEventListener('click', () => {
            if (currentInspectionTx) updateStatus(currentInspectionTx.id, 'FLAGGED');
        });
        document.getElementById('btn-reject-tx')?.addEventListener('click', () => {
            if (currentInspectionTx) updateStatus(currentInspectionTx.id, 'REJECTED');
        });

        // Listen for global inspection events
        window.addEventListener('fintrust:inspect-transaction', (e) => {
            if (e.detail && e.detail.id) {
                inspectTransaction(parseInt(e.detail.id, 10));
            }
        });
    }

    return {
        init: () => {
            bindEvents();
            loadTransactions();
        },
        loadTransactions,
        inspectTransaction
    };
})();
