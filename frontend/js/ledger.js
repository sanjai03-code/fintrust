/**
 * FinTrust - Double-Entry Ledger Verification & Reconciliation Module
 */

const LedgerModule = (() => {
    let ledgerEntries = [];

    async function loadLedger() {
        const tbody = document.getElementById('ledger-tbody');
        if (tbody) tbody.innerHTML = '<tr><td colspan="8" class="text-center py-4 text-muted"><span class="spinner-border spinner-border-sm me-2"></span>Loading double-entry ledger...</td></tr>';

        try {
            const res = await FinTrustAPI.ledger.list();
            if (res.success) {
                ledgerEntries = res.entries || [];
                renderTable(ledgerEntries);
            }
        } catch (err) {
            console.error('[LedgerModule] Error loading ledger:', err);
            if (tbody) tbody.innerHTML = `<tr><td colspan="8" class="text-center text-danger py-4">Failed to load ledger: ${err.message}</td></tr>`;
        }
    }

    function renderTable(entries) {
        const tbody = document.getElementById('ledger-tbody');
        if (!tbody) return;

        if (entries.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center py-4 text-muted">No ledger entries recorded yet.</td></tr>';
            return;
        }

        tbody.innerHTML = entries.map(entry => {
            const isDebit = entry.entry_type === 'DEBIT';
            const typeBadge = isDebit 
                ? '<span class="badge bg-danger-subtle text-danger border border-danger-subtle fw-bold">DEBIT (-)</span>' 
                : '<span class="badge bg-success-subtle text-success border border-success-subtle fw-bold">CREDIT (+)</span>';
            const verifiedBadge = entry.is_verified 
                ? '<span class="badge bg-success"><i class="bi bi-check2-circle"></i> VERIFIED</span>' 
                : '<span class="badge bg-warning text-dark"><i class="bi bi-clock-history"></i> UNVERIFIED</span>';

            return `
                <tr>
                    <td class="mono-text fw-bold">${entry.transaction_ref}</td>
                    <td>${typeBadge}</td>
                    <td class="mono-text">${entry.account_number}</td>
                    <td class="fw-bold ${isDebit ? 'text-danger' : 'text-success'}">
                        ${isDebit ? '-' : '+'}${entry.currency} ${Number(entry.amount).toLocaleString('en-US', { minimumFractionDigits: 2 })}
                    </td>
                    <td class="mono-text text-muted">${entry.currency} ${Number(entry.balance_after || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                    <td>${verifiedBadge}</td>
                    <td class="small text-muted">${entry.verified_by_name ? `By: ${entry.verified_by_name}` : (entry.verification_notes || 'Pending audit')}</td>
                    <td class="small text-muted">${entry.created_at ? new Date(entry.created_at).toLocaleDateString() : ''}</td>
                </tr>
            `;
        }).join('');
    }

    async function runVerificationEngine(autoReconcile = false) {
        const reportContainer = document.getElementById('verification-report-container');
        if (reportContainer) {
            reportContainer.innerHTML = '<div class="text-center py-3"><span class="spinner-border spinner-border-sm me-2"></span>Running automated verification algorithm across all ledger accounts...</div>';
            reportContainer.style.display = 'block';
        }

        try {
            const res = await FinTrustAPI.ledger.verify(null, autoReconcile);
            if (res.success && res.verification_report) {
                renderVerificationReport(res.verification_report, autoReconcile);
                await loadLedger();
                if (window.DashboardModule) DashboardModule.loadDashboard();
            }
        } catch (err) {
            if (reportContainer) reportContainer.innerHTML = `<div class="alert alert-danger">Verification failed: ${err.message}</div>`;
        }
    }

    function renderVerificationReport(items, wasAutoReconciled) {
        const container = document.getElementById('verification-report-container');
        if (!container) return;

        let balancedCount = 0;
        let imbalancedCount = 0;
        let duplicateCount = 0;

        items.forEach(it => {
            if (it.is_balanced) balancedCount++;
            else imbalancedCount++;
            if (it.is_duplicate) duplicateCount++;
        });

        container.innerHTML = `
            <div class="card card-fintrust mb-4 border-primary">
                <div class="card-header bg-primary text-white d-flex align-items-center justify-content-between">
                    <h6 class="mb-0 text-white"><i class="bi bi-shield-check me-2"></i>Ledger Reconciliation & Integrity Report</h6>
                    <span class="badge bg-white text-primary fw-bold">${items.length} Transactions Audited</span>
                </div>
                <div class="card-body">
                    <div class="row g-3 mb-3">
                        <div class="col-md-4">
                            <div class="p-3 bg-success-subtle rounded border border-success-subtle text-center">
                                <div class="fs-4 fw-bold text-success">${balancedCount}</div>
                                <div class="small fw-semibold text-success-emphasis">Perfect Double-Entry Balance</div>
                            </div>
                        </div>
                        <div class="col-md-4">
                            <div class="p-3 ${imbalancedCount > 0 ? 'bg-danger-subtle border-danger-subtle text-danger' : 'bg-light'} rounded border text-center">
                                <div class="fs-4 fw-bold">${imbalancedCount}</div>
                                <div class="small fw-semibold">Imbalances / Single-Entry Flags</div>
                            </div>
                        </div>
                        <div class="col-md-4">
                            <div class="p-3 ${duplicateCount > 0 ? 'bg-warning-subtle border-warning-subtle text-warning-emphasis' : 'bg-light'} rounded border text-center">
                                <div class="fs-4 fw-bold">${duplicateCount}</div>
                                <div class="small fw-semibold">Potential Duplicate Postings</div>
                            </div>
                        </div>
                    </div>

                    <div class="table-responsive">
                        <table class="table table-sm table-bordered">
                            <thead class="table-light">
                                <tr>
                                    <th>Transaction Ref</th>
                                    <th>Expected Amount</th>
                                    <th>Total Debit</th>
                                    <th>Total Credit</th>
                                    <th>Variance</th>
                                    <th>Duplicate Check</th>
                                    <th>Status</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${items.map(it => `
                                    <tr class="${it.is_balanced ? '' : 'table-danger'}">
                                        <td class="mono-text fw-bold">${it.transaction_ref}</td>
                                        <td>$${Number(it.expected_amount).toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                                        <td class="text-danger">$${Number(it.total_debit).toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                                        <td class="text-success">$${Number(it.total_credit).toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                                        <td class="fw-bold ${Math.abs(it.variance) > 0 ? 'text-danger' : 'text-success'}">
                                            $${Number(it.variance).toLocaleString('en-US', { minimumFractionDigits: 2 })}
                                        </td>
                                        <td>
                                            ${it.is_duplicate 
                                                ? '<span class="badge bg-warning text-dark"><i class="bi bi-files"></i> Duplicate Pattern</span>' 
                                                : '<span class="text-muted small"><i class="bi bi-check"></i> Unique</span>'}
                                        </td>
                                        <td>
                                            ${it.is_balanced 
                                                ? '<span class="badge bg-success">BALANCED</span>' 
                                                : '<span class="badge bg-danger">IMBALANCE DETECTED</span>'}
                                        </td>
                                    </tr>
                                `).join('')}
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        `;
    }

    function bindEvents() {
        document.getElementById('btn-run-ledger-verify')?.addEventListener('click', () => {
            runVerificationEngine(false);
        });

        document.getElementById('btn-auto-reconcile')?.addEventListener('click', () => {
            if (confirm('Verify and mark all balanced ledger records as audited?')) {
                runVerificationEngine(true);
            }
        });
    }

    return {
        init: () => {
            bindEvents();
            loadLedger();
        },
        loadLedger,
        runVerificationEngine
    };
})();
