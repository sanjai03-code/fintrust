/**
 * FinTrust - Unified API Client & Gateway Abstraction
 * Handles JWT authorization, routing, and error states
 */

const FinTrustAPI = (() => {
    // Determine API Base URL dynamically
    const API_BASE = window.location.origin;

    const getToken = () => localStorage.getItem('fintrust_token');
    const setToken = (t) => localStorage.setItem('fintrust_token', t);
    const removeToken = () => localStorage.removeItem('fintrust_token');
    const getUser = () => {
        try {
            return JSON.parse(localStorage.getItem('fintrust_user') || 'null');
        } catch (e) {
            return null;
        }
    };
    const setUser = (u) => localStorage.setItem('fintrust_user', JSON.stringify(u));

    async function request(endpoint, options = {}) {
        const url = `${API_BASE}${endpoint}`;
        const headers = options.headers || {};

        const token = getToken();
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        if (!(options.body instanceof FormData) && !headers['Content-Type']) {
            headers['Content-Type'] = 'application/json';
        }

        try {
            const resp = await fetch(url, { ...options, headers });
            const data = await resp.json().catch(() => ({}));

            if (resp.status === 401) {
                // Token expired or invalid
                console.warn('[FinTrustAPI] Session expired or unauthorized');
                if (!endpoint.includes('/api/auth/login')) {
                    removeToken();
                    window.dispatchEvent(new CustomEvent('fintrust:auth-expired'));
                }
            }

            if (!resp.ok && !data.success) {
                throw new Error(data.error || `Request failed with status ${resp.status}`);
            }

            return data;
        } catch (err) {
            console.error(`[FinTrustAPI Error] ${endpoint}:`, err);
            throw err;
        }
    }

    return {
        // Storage & Session
        getToken,
        setToken,
        removeToken,
        getUser,
        setUser,

        // Authentication
        auth: {
            login: (email, password) => request('/api/auth/login', {
                method: 'POST',
                body: JSON.stringify({ email, password })
            }),
            register: (data) => request('/api/auth/register', {
                method: 'POST',
                body: JSON.stringify(data)
            }),
            me: () => request('/api/auth/me'),
            listUsers: () => request('/api/auth/users')
        },

        // Organizations
        orgs: {
            list: () => request('/api/orgs'),
            get: (id) => request(`/api/orgs/${id}`),
            create: (data) => request('/api/orgs', {
                method: 'POST',
                body: JSON.stringify(data)
            }),
            update: (id, data) => request(`/api/orgs/${id}`, {
                method: 'PUT',
                body: JSON.stringify(data)
            }),
            listEmployees: (orgId) => request(`/api/orgs/${orgId}/employees`),
            addEmployee: (orgId, data) => request(`/api/orgs/${orgId}/employees`, {
                method: 'POST',
                body: JSON.stringify(data)
            })
        },

        // Transactions (Java Microservice)
        transactions: {
            list: (params = {}) => {
                const query = new URLSearchParams(params).toString();
                return request(`/api/transactions${query ? '?' + query : ''}`);
            },
            create: (data) => request('/api/transactions', {
                method: 'POST',
                body: JSON.stringify(data)
            }),
            updateStatus: (id, status) => request(`/api/transactions/${id}/status`, {
                method: 'PUT',
                body: JSON.stringify({ status })
            })
        },

        // Ledger Verification (Java Microservice)
        ledger: {
            list: () => request('/api/ledger'),
            verify: (txId = null, autoReconcile = false) => request('/api/ledger/verify', {
                method: 'POST',
                body: JSON.stringify({ transaction_id: txId, auto_reconcile: autoReconcile })
            })
        },

        // Risk & Alerts (Java Microservice)
        risk: {
            listAlerts: (status = null) => {
                const query = status ? `?status=${encodeURIComponent(status)}` : '';
                return request(`/api/risk/alerts${query}`);
            },
            resolveAlert: (alertId, status, notes) => request(`/api/risk/alerts/${alertId}/resolve`, {
                method: 'POST',
                body: JSON.stringify({ status, resolution_notes: notes })
            }),
            evaluate: (data) => request('/api/risk/evaluate', {
                method: 'POST',
                body: JSON.stringify(data)
            })
        },

        // Audit Trail (Java Microservice)
        audit: {
            list: (params = {}) => {
                const query = new URLSearchParams(params).toString();
                return request(`/api/audit${query ? '?' + query : ''}`);
            }
        },

        // Documents & S3 Storage
        documents: {
            list: () => request('/api/documents'),
            upload: (formData) => request('/api/documents/upload', {
                method: 'POST',
                body: formData
            })
        },

        // Reports
        reports: {
            summary: () => request('/api/reports/summary'),
            compliance: () => request('/api/reports/compliance')
        },

        // Health
        system: {
            healthPhp: () => request('/api/health'),
            healthJava: () => fetch('http://127.0.0.1:8080/api/health').then(r => r.json()).catch(() => ({ status: 'STANDALONE_STANDBY' }))
        }
    };
})();
