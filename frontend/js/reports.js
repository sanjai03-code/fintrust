/**
 * FinTrust - Compliance Reporting & S3 Document Vault Module
 */

const ReportsModule = (() => {
    let documents = [];

    async function loadReportsAndDocuments() {
        await loadDocuments();
        await loadComplianceData();
    }

    async function loadDocuments() {
        const tbody = document.getElementById('documents-tbody');
        if (tbody) tbody.innerHTML = '<tr><td colspan="7" class="text-center py-4 text-muted"><span class="spinner-border spinner-border-sm me-2"></span>Loading S3 document vault...</td></tr>';

        try {
            const res = await FinTrustAPI.documents.list();
            if (res.success) {
                documents = res.documents || [];
                renderDocumentsTable(documents);
            }
        } catch (err) {
            console.error('[ReportsModule] Error loading documents:', err);
            if (tbody) tbody.innerHTML = `<tr><td colspan="7" class="text-center text-danger py-4">Failed to load documents: ${err.message}</td></tr>`;
        }
    }

    function renderDocumentsTable(docs) {
        const tbody = document.getElementById('documents-tbody');
        if (!tbody) return;

        if (docs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center py-4 text-muted">No documents stored in S3 vault yet. Click "Upload Document" to attach an invoice or KYC form.</td></tr>';
            return;
        }

        tbody.innerHTML = docs.map(doc => {
            const sizeKb = (doc.file_size / 1024).toFixed(1) + ' KB';
            const shortChecksum = doc.checksum ? doc.checksum.substring(0, 16) + '...' : 'N/A';

            return `
                <tr>
                    <td class="fw-bold">
                        <i class="bi bi-file-earmark-pdf text-danger me-2"></i>${doc.file_name}
                    </td>
                    <td><span class="badge bg-light text-dark border">${doc.document_category}</span></td>
                    <td class="mono-text small text-muted">${doc.s3_bucket}/${doc.s3_key}</td>
                    <td>${sizeKb}</td>
                    <td class="mono-text small" title="${doc.checksum}">
                        <code>${shortChecksum}</code>
                    </td>
                    <td><span class="badge bg-success">${doc.status}</span></td>
                    <td class="small text-muted">${doc.uploaded_at ? new Date(doc.uploaded_at).toLocaleDateString() : ''}</td>
                </tr>
            `;
        }).join('');
    }

    async function loadComplianceData() {
        const container = document.getElementById('compliance-statement-content');
        if (!container) return;

        try {
            const res = await FinTrustAPI.reports.compliance();
            if (res.success) {
                renderComplianceStatement(res);
            }
        } catch (err) {
            container.innerHTML = `<div class="text-danger small">Compliance data unavailable: ${err.message}</div>`;
        }
    }

    function renderComplianceStatement(data) {
        const container = document.getElementById('compliance-statement-content');
        if (!container) return;

        const openAlerts = data.unresolved_alerts || [];
        const flagged = data.flagged_transactions || [];

        container.innerHTML = `
            <div class="p-3 bg-light rounded border mb-3">
                <div class="d-flex justify-content-between align-items-center mb-2">
                    <span class="fw-bold text-dark">Regulatory Standard:</span>
                    <span class="badge bg-primary">${data.regulatory_standard}</span>
                </div>
                <div class="d-flex justify-content-between align-items-center mb-2">
                    <span class="fw-bold text-dark">Generated Timestamp:</span>
                    <span class="mono-text small">${new Date(data.report_generated_at).toUTCString()}</span>
                </div>
                <div class="d-flex justify-content-between align-items-center">
                    <span class="fw-bold text-dark">Active Surveillance Flags:</span>
                    <span class="badge ${flagged.length > 0 ? 'bg-danger' : 'bg-success'}">${flagged.length} Critical Items</span>
                </div>
            </div>

            <h6 class="fw-bold text-dark mb-2">Flagged Higher-Risk Exposures (Risk Score ≥ 70)</h6>
            <div class="table-responsive">
                <table class="table table-sm table-bordered mb-0">
                    <thead class="table-light">
                        <tr>
                            <th>Ref</th>
                            <th>Entity</th>
                            <th>Amount</th>
                            <th>Risk Score</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${flagged.map(f => `
                            <tr>
                                <td class="mono-text fw-bold text-danger">${f.transaction_ref}</td>
                                <td>${f.organization_name}</td>
                                <td class="fw-bold">${f.currency} ${Number(f.amount).toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                                <td><span class="badge-ft badge-risk-critical">${Number(f.total_score).toFixed(1)}/100</span></td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>
        `;
    }

    function bindEvents() {
        // Upload Document Form
        const uploadForm = document.getElementById('uploadDocumentForm');
        if (uploadForm) {
            uploadForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                const btn = uploadForm.querySelector('button[type="submit"]');
                btn.disabled = true;

                const fileInput = document.getElementById('doc-file');
                if (!fileInput.files.length) {
                    alert('Please select a file to upload');
                    btn.disabled = false;
                    return;
                }

                const formData = new FormData();
                formData.append('file', fileInput.files[0]);
                formData.append('organization_id', document.getElementById('doc-org').value);
                formData.append('document_category', document.getElementById('doc-category').value);

                try {
                    const res = await FinTrustAPI.documents.upload(formData);
                    if (res.success) {
                        const modalEl = document.getElementById('uploadDocModal');
                        if (modalEl) bootstrap.Modal.getInstance(modalEl).hide();
                        uploadForm.reset();
                        await loadDocuments();
                    }
                } catch (err) {
                    alert('Upload failed: ' + err.message);
                } finally {
                    btn.disabled = false;
                }
            });
        }

        // Print Statement Button
        document.getElementById('btn-print-compliance')?.addEventListener('click', () => {
            window.print();
        });
    }

    return {
        init: () => {
            bindEvents();
            loadReportsAndDocuments();
        },
        loadReportsAndDocuments
    };
})();
