/**
 * FinTrust - Organization & Employee Management Module
 */

const OrganizationsModule = (() => {
    let organizations = [];
    let selectedOrgId = null;

    async function loadOrganizations() {
        const tbody = document.getElementById('orgs-tbody');
        if (tbody) tbody.innerHTML = '<tr><td colspan="7" class="text-center py-4 text-muted"><span class="spinner-border spinner-border-sm me-2"></span>Loading organizations...</td></tr>';

        try {
            const res = await FinTrustAPI.orgs.list();
            if (res.success) {
                organizations = res.organizations || [];
                renderTable(organizations);
                populateOrgDropdowns(organizations);
            }
        } catch (err) {
            console.error('[OrganizationsModule] Error loading orgs:', err);
            if (tbody) tbody.innerHTML = `<tr><td colspan="7" class="text-center text-danger py-4">Failed to load organizations: ${err.message}</td></tr>`;
        }
    }

    function renderTable(orgs) {
        const tbody = document.getElementById('orgs-tbody');
        if (!tbody) return;

        if (orgs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center py-4 text-muted">No organizations found. Click "Create Organization" to get started.</td></tr>';
            return;
        }

        tbody.innerHTML = orgs.map(org => {
            const riskClass = `badge-risk-${(org.risk_tier || 'low').toLowerCase()}`;
            return `
                <tr>
                    <td class="fw-bold text-dark">${org.name}</td>
                    <td class="mono-text">${org.registration_number}</td>
                    <td><span class="badge bg-light text-dark border">${org.country} / ${org.currency}</span></td>
                    <td><span class="badge-ft ${riskClass}">${org.risk_tier}</span></td>
                    <td><span class="badge bg-secondary rounded-pill px-2 py-1">${org.employee_count || 0} Members</span></td>
                    <td class="fw-bold">$${Number(org.annual_turnover || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                    <td>
                        <button class="btn btn-sm btn-ft-primary py-1 px-2 btn-manage-org" data-id="${org.id}">
                            <i class="bi bi-people"></i> Manage
                        </button>
                    </td>
                </tr>
            `;
        }).join('');

        tbody.querySelectorAll('.btn-manage-org').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = parseInt(btn.getAttribute('data-id'), 10);
                openOrgDetails(id);
            });
        });
    }

    function populateOrgDropdowns(orgs) {
        document.querySelectorAll('.select-org-options').forEach(select => {
            const currentVal = select.value;
            select.innerHTML = orgs.map(o => `<option value="${o.id}">${o.name} (${o.currency})</option>`).join('');
            if (currentVal) select.value = currentVal;
        });
    }

    async function openOrgDetails(orgId) {
        selectedOrgId = orgId;
        const org = organizations.find(o => o.id === orgId);
        if (!org) return;

        const modalEl = document.getElementById('orgDetailsModal');
        if (!modalEl) return;

        document.getElementById('org-detail-title').textContent = org.name;
        document.getElementById('org-detail-reg').textContent = org.registration_number;
        document.getElementById('org-detail-tax').textContent = org.tax_id;
        document.getElementById('org-detail-country').textContent = `${org.country} (${org.currency})`;
        document.getElementById('org-detail-risk').textContent = org.risk_tier;
        document.getElementById('org-detail-contact').textContent = org.compliance_contact || 'N/A';

        // Load employees
        const empTbody = document.getElementById('org-employees-tbody');
        empTbody.innerHTML = '<tr><td colspan="5" class="text-center py-3"><span class="spinner-border spinner-border-sm me-2"></span>Loading roster...</td></tr>';

        try {
            const empRes = await FinTrustAPI.orgs.listEmployees(orgId);
            if (empRes.success) {
                renderEmployees(empRes.employees);
            }
        } catch (e) {
            empTbody.innerHTML = '<tr><td colspan="5" class="text-center text-danger py-3">Error loading roster</td></tr>';
        }

        const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
        modal.show();
    }

    function renderEmployees(employees) {
        const tbody = document.getElementById('org-employees-tbody');
        if (!tbody) return;

        if (employees.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center py-3 text-muted">No employees assigned to this organization yet.</td></tr>';
            return;
        }

        tbody.innerHTML = employees.map(e => `
            <tr>
                <td class="fw-bold">${e.full_name}</td>
                <td>${e.email}</td>
                <td>${e.department}</td>
                <td><span class="badge bg-primary">${e.designation}</span></td>
                <td><span class="badge bg-light text-dark border">${e.org_role}</span></td>
            </tr>
        `).join('');
    }

    function bindEvents() {
        // Create Org Form
        const createForm = document.getElementById('createOrgForm');
        if (createForm) {
            createForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                const btn = createForm.querySelector('button[type="submit"]');
                btn.disabled = true;

                const data = {
                    name: document.getElementById('org-name').value,
                    registration_number: document.getElementById('org-regnum').value,
                    tax_id: document.getElementById('org-taxid').value,
                    country: document.getElementById('org-country').value,
                    currency: document.getElementById('org-currency').value,
                    risk_tier: document.getElementById('org-risktier').value,
                    compliance_contact: document.getElementById('org-contact').value,
                    annual_turnover: parseFloat(document.getElementById('org-turnover').value || 0)
                };

                try {
                    const res = await FinTrustAPI.orgs.create(data);
                    if (res.success) {
                        const modalEl = document.getElementById('createOrgModal');
                        if (modalEl) bootstrap.Modal.getInstance(modalEl).hide();
                        createForm.reset();
                        await loadOrganizations();
                    }
                } catch (err) {
                    alert('Error creating organization: ' + err.message);
                } finally {
                    btn.disabled = false;
                }
            });
        }

        // Add Employee Button
        const addEmpBtn = document.getElementById('btn-add-employee-trigger');
        if (addEmpBtn) {
            addEmpBtn.addEventListener('click', async () => {
                // Populate user list
                try {
                    const uRes = await FinTrustAPI.auth.listUsers();
                    if (uRes.success) {
                        const userSelect = document.getElementById('assign-user-id');
                        userSelect.innerHTML = (uRes.users || []).map(u => `
                            <option value="${u.id}">${u.full_name} (${u.email}) - ${u.role}</option>
                        `).join('');
                    }
                } catch (e) {}

                const addModal = bootstrap.Modal.getOrCreateInstance(document.getElementById('addEmployeeModal'));
                addModal.show();
            });
        }

        // Add Employee Form Submit
        const addEmpForm = document.getElementById('addEmployeeForm');
        if (addEmpForm) {
            addEmpForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                if (!selectedOrgId) return;

                const data = {
                    user_id: parseInt(document.getElementById('assign-user-id').value, 10),
                    department: document.getElementById('assign-dept').value,
                    designation: document.getElementById('assign-designation').value,
                    role: document.getElementById('assign-role').value
                };

                try {
                    const res = await FinTrustAPI.orgs.addEmployee(selectedOrgId, data);
                    if (res.success) {
                        bootstrap.Modal.getInstance(document.getElementById('addEmployeeModal')).hide();
                        addEmpForm.reset();
                        openOrgDetails(selectedOrgId);
                        loadOrganizations();
                    }
                } catch (err) {
                    alert('Error assigning employee: ' + err.message);
                }
            });
        }
    }

    return {
        init: () => {
            bindEvents();
            loadOrganizations();
        },
        loadOrganizations,
        openOrgDetails
    };
})();
