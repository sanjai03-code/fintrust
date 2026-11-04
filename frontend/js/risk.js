/**
 * FinTrust - Risk Analysis & Suspicious Transaction Alerts Module
 */

const RiskModule = (() => {
    let alerts = [];
    let currentAlert = null;

    async function loadAlerts() {
        const tbody = document.getElementById('alerts-tbody');
        if (tbody) tbody.innerHTML = '<tr><td colspan="7" class="text-center py-4 text-muted"><span class="spinner-border spinner-border-sm me-2"></span>Loading alerts...</td></tr>';

        const statusFilter = document.getElementById('filter-alert-status')?.value || '';

        try {
            const res = await FinTrustAPI.risk.listAlerts(statusFilter);
            if (res.success) {
                alerts = res.alerts || [];
                renderAlertsTable(alerts);
            }
        } catch (err) {
            console.error('[RiskModule] Error loading alerts:', err);
            if (tbody) tbody.innerHTML = `<tr><td colspan="7" class="text-center text-danger py-4">Failed to load alerts: ${err.message}</td></tr>`;
        }
    }

    function renderAlertsTable(list) {
        const tbody = document.getElementById('alerts-tbody');
        if (!tbody) return;

        if (list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center py-4 text-muted">No alerts found for the selected criteria.</td></tr>';
            return;
        }

        tbody.innerHTML = list.map(a => {
            const sevBadge = a.severity === 'CRITICAL' 
                ? '<span class="badge-ft badge-risk-critical">CRITICAL</span>' 
                : (a.severity === 'HIGH' ? '<span class="badge-ft badge-risk-high">HIGH</span>' 
                : (a.severity === 'MEDIUM' ? '<span class="badge-ft badge-risk-medium">MEDIUM</span>' 
                : '<span class="badge-ft badge-risk-low">LOW</span>'));

            const statusBadge = a.status === 'NEW' 
                ? '<span class="badge bg-danger">NEW</span>' 
                : (a.status === 'IN_REVIEW' ? '<span class="badge bg-warning text-dark">IN REVIEW</span>' 
                : (a.status === 'RESOLVED' ? '<span class="badge bg-success">RESOLVED</span>' 
                : '<span class="badge bg-secondary">DISMISSED</span>'));

            return `
                <tr>
                    <td class="mono-text fw-bold text-danger">${a.alert_code}</td>
                    <td>${sevBadge}</td>
                    <td>
                        <div class="fw-bold text-dark">${a.title}</div>
                        <div class="small text-muted">${a.description}</div>
                    </td>
                    <td>
                        <div class="mono-text fw-semibold">${a.transaction_ref || 'N/A'}</div>
                        <div class="small text-muted">${a.currency || 'USD'} ${Number(a.amount || 0).toLocaleString()}</div>
                    </td>
                    <td>${statusBadge}</td>
                    <td class="small text-muted">${a.resolved_by_name ? `Resolved by: ${a.resolved_by_name}` : 'Pending assignment'}</td>
                    <td>
                        <button class="btn btn-sm btn-ft-primary py-1 px-2 btn-review-alert" data-id="${a.id}">
                            <i class="bi bi-pencil-square"></i> Review
                        </button>
                    </td>
                </tr>
            `;
        }).join('');

        tbody.querySelectorAll('.btn-review-alert').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = parseInt(btn.getAttribute('data-id'), 10);
                openReviewModal(id);
            });
        });
    }

    function openReviewModal(alertId) {
        const alertObj = alerts.find(a => a.id === alertId);
        if (!alertObj) return;

        currentAlert = alertObj;
        const modalEl = document.getElementById('reviewAlertModal');
        if (!modalEl) return;

        document.getElementById('modal-alert-code').textContent = alertObj.alert_code;
        document.getElementById('modal-alert-title').textContent = alertObj.title;
        document.getElementById('modal-alert-desc').textContent = alertObj.description;
        document.getElementById('modal-alert-tx').textContent = alertObj.transaction_ref || 'N/A';
        document.getElementById('modal-alert-severity').textContent = alertObj.severity;
        document.getElementById('alert-resolution-status').value = alertObj.status === 'NEW' ? 'IN_REVIEW' : alertObj.status;
        document.getElementById('alert-resolution-notes').value = alertObj.resolution_notes || '';

        const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
        modal.show();
    }

    // Interactive Simulator
    async function runSimulator() {
        const amount = parseFloat(document.getElementById('sim-amount')?.value || 0);
        const country = document.getElementById('sim-country')?.value || 'USA';
        const type = document.getElementById('sim-type')?.value || 'TRANSFER';
        const src = document.getElementById('sim-src')?.value || 'ACC-TEST-1';
        const dst = document.getElementById('sim-dst')?.value || 'ACC-TEST-2';

        try {
            const res = await FinTrustAPI.risk.evaluate({
                amount,
                destination_country: country,
                type,
                source_account: src,
                destination_account: dst
            });

            if (res.success && res.risk_evaluation) {
                const ev = res.risk_evaluation;
                const score = Number(ev.score).toFixed(1);
                document.getElementById('sim-score').textContent = score + '/100';
                document.getElementById('sim-tier').textContent = ev.tier;

                const meterFill = document.getElementById('sim-meter-fill');
                meterFill.style.width = Math.min(100, Math.max(5, score)) + '%';

                if (ev.tier === 'CRITICAL') meterFill.style.backgroundColor = '#881337';
                else if (ev.tier === 'HIGH') meterFill.style.backgroundColor = '#EF4444';
                else if (ev.tier === 'MEDIUM') meterFill.style.backgroundColor = '#F59E0B';
                else meterFill.style.backgroundColor = '#10B981';

                const rulesContainer = document.getElementById('sim-rules-list');
                rulesContainer.innerHTML = (ev.rules || []).map(r => `<li class="mb-1">${r}</li>`).join('');
            }
        } catch (e) {
            console.error(e);
        }
    }

    function bindEvents() {
        document.getElementById('filter-alert-status')?.addEventListener('change', loadAlerts);

        // Simulator controls
        ['sim-amount', 'sim-country', 'sim-type'].forEach(id => {
            document.getElementById(id)?.addEventListener('input', runSimulator);
            document.getElementById(id)?.addEventListener('change', runSimulator);
        });

        // Review Form Submit
        const reviewForm = document.getElementById('reviewAlertForm');
        if (reviewForm) {
            reviewForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                if (!currentAlert) return;

                const status = document.getElementById('alert-resolution-status').value;
                const notes = document.getElementById('alert-resolution-notes').value;

                try {
                    const res = await FinTrustAPI.risk.resolveAlert(currentAlert.id, status, notes);
                    if (res.success) {
                        const modalEl = document.getElementById('reviewAlertModal');
                        if (modalEl) bootstrap.Modal.getInstance(modalEl).hide();
                        await loadAlerts();
                        if (window.DashboardModule) DashboardModule.loadDashboard();
                    }
                } catch (err) {
                    alert('Error saving resolution: ' + err.message);
                }
            });
        }

        window.addEventListener('fintrust:review-alert', (e) => {
            if (e.detail && e.detail.id) {
                openReviewModal(parseInt(e.detail.id, 10));
            }
        });
    }

    return {
        init: () => {
            bindEvents();
            loadAlerts();
            runSimulator();
        },
        loadAlerts,
        openReviewModal
    };
})();
