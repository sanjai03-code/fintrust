/**
 * FinTrust - Immutable Audit Trail Module
 */

const AuditModule = (() => {
    let auditLogs = [];

    async function loadAuditLogs() {
        const tbody = document.getElementById('audit-tbody');
        if (tbody) tbody.innerHTML = '<tr><td colspan="7" class="text-center py-4 text-muted"><span class="spinner-border spinner-border-sm me-2"></span>Loading immutable audit records...</td></tr>';

        const entityType = document.getElementById('filter-audit-entity')?.value || '';
        const action = document.getElementById('filter-audit-action')?.value || '';

        try {
            const params = {};
            if (entityType) params.entity_type = entityType;
            if (action) params.action = action;

            const res = await FinTrustAPI.audit.list(params);
            if (res.success) {
                auditLogs = res.audit_logs || [];
                renderTable(auditLogs);
            }
        } catch (err) {
            console.error('[AuditModule] Error loading audit logs:', err);
            if (tbody) tbody.innerHTML = `<tr><td colspan="7" class="text-center text-danger py-4">Failed to load audit logs: ${err.message}</td></tr>`;
        }
    }

    function renderTable(logs) {
        const tbody = document.getElementById('audit-tbody');
        if (!tbody) return;

        if (logs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center py-4 text-muted">No audit trail records match the filter.</td></tr>';
            return;
        }

        tbody.innerHTML = logs.map(log => {
            const formattedDate = log.created_at ? new Date(log.created_at).toLocaleString() : 'N/A';
            const hasDiff = log.old_values || log.new_values;

            return `
                <tr>
                    <td class="mono-text small text-muted">#LOG-${log.id}</td>
                    <td>
                        <span class="badge bg-dark-subtle text-dark border fw-bold">${log.action}</span>
                    </td>
                    <td><span class="badge bg-light text-secondary border">${log.entity_type} [${log.entity_id}]</span></td>
                    <td>
                        <div class="fw-semibold text-dark">${log.user_name || 'System / Service'}</div>
                        <div class="small text-muted">${log.user_email || 'cron@fintrust.io'}</div>
                    </td>
                    <td class="mono-text small text-muted">${log.ip_address || '127.0.0.1'}</td>
                    <td class="small text-muted">${formattedDate}</td>
                    <td>
                        ${hasDiff ? `
                            <button class="btn btn-sm btn-ft-secondary py-1 px-2 btn-view-diff" data-id="${log.id}">
                                <i class="bi bi-file-earmark-diff"></i> Inspect Diff
                            </button>
                        ` : '<span class="text-muted small">Initial State</span>'}
                    </td>
                </tr>
            `;
        }).join('');

        tbody.querySelectorAll('.btn-view-diff').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = parseInt(btn.getAttribute('data-id'), 10);
                showDiffModal(id);
            });
        });
    }

    function showDiffModal(logId) {
        const log = auditLogs.find(l => l.id === logId);
        if (!log) return;

        const modalEl = document.getElementById('auditDiffModal');
        if (!modalEl) return;

        document.getElementById('diff-log-action').textContent = `${log.action} on ${log.entity_type} #${log.entity_id}`;
        document.getElementById('diff-log-user').textContent = `${log.user_name || 'System'} (${log.user_email || 'cron'})`;
        document.getElementById('diff-log-time').textContent = log.created_at ? new Date(log.created_at).toLocaleString() : '';

        const oldContainer = document.getElementById('diff-old-values');
        const newContainer = document.getElementById('diff-new-values');

        try {
            oldContainer.textContent = log.old_values ? JSON.stringify(JSON.parse(log.old_values), null, 2) : 'null (Created)';
        } catch (e) {
            oldContainer.textContent = log.old_values || 'null';
        }

        try {
            newContainer.textContent = log.new_values ? JSON.stringify(JSON.parse(log.new_values), null, 2) : 'null (Deleted)';
        } catch (e) {
            newContainer.textContent = log.new_values || 'null';
        }

        const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
        modal.show();
    }

    function bindEvents() {
        document.getElementById('filter-audit-entity')?.addEventListener('change', loadAuditLogs);
        document.getElementById('filter-audit-action')?.addEventListener('change', loadAuditLogs);
    }

    return {
        init: () => {
            bindEvents();
            loadAuditLogs();
        },
        loadAuditLogs
    };
})();
