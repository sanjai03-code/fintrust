/**
 * FinTrust - Authentication & User Management Module
 */

const AuthModule = (() => {
    let currentUser = null;

    async function init() {
        bindEvents();
        await checkAuthStatus();
    }

    async function checkAuthStatus() {
        const token = FinTrustAPI.getToken();
        if (!token) {
            showLoginModal();
            return;
        }

        try {
            const data = await FinTrustAPI.auth.me();
            if (data.success && data.user) {
                currentUser = data.user;
                FinTrustAPI.setUser(currentUser);
                updateUIForUser(currentUser);
                window.dispatchEvent(new CustomEvent('fintrust:user-authenticated', { detail: currentUser }));
            } else {
                showLoginModal();
            }
        } catch (e) {
            console.warn('[AuthModule] Session check failed, requiring login');
            showLoginModal();
        }
    }

    function updateUIForUser(user) {
        const nameEl = document.getElementById('topbar-user-name');
        const roleEl = document.getElementById('topbar-user-role');
        const avatarEl = document.getElementById('topbar-user-avatar');

        if (nameEl) nameEl.textContent = user.full_name;
        if (roleEl) roleEl.textContent = formatRole(user.role);
        if (avatarEl) {
            const initials = user.full_name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
            avatarEl.textContent = initials || 'FT';
        }

        // Role-based visibility adjustments
        document.querySelectorAll('[data-role-required]').forEach(el => {
            const allowed = el.getAttribute('data-role-required').split(',');
            if (user.role === 'ADMIN' || allowed.includes(user.role)) {
                el.style.display = '';
            } else {
                el.style.display = 'none';
            }
        });

        // Hide auth modal if open
        const modalEl = document.getElementById('authModal');
        if (modalEl) {
            const modal = bootstrap.Modal.getInstance(modalEl);
            if (modal) modal.hide();
        }
    }

    function formatRole(role) {
        return role.replace(/_/g, ' ');
    }

    function showLoginModal() {
        const modalEl = document.getElementById('authModal');
        if (modalEl) {
            const modal = bootstrap.Modal.getOrCreateInstance(modalEl, { backdrop: 'static', keyboard: false });
            modal.show();
        }
    }

    function bindEvents() {
        // Login Form Submit
        const loginForm = document.getElementById('loginForm');
        if (loginForm) {
            loginForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                const email = document.getElementById('login-email').value;
                const pass = document.getElementById('login-password').value;
                const errEl = document.getElementById('login-error');
                errEl.style.display = 'none';

                try {
                    const res = await FinTrustAPI.auth.login(email, pass);
                    if (res.success) {
                        FinTrustAPI.setToken(res.token);
                        FinTrustAPI.setUser(res.user);
                        currentUser = res.user;
                        updateUIForUser(currentUser);
                        window.dispatchEvent(new CustomEvent('fintrust:user-authenticated', { detail: currentUser }));
                    }
                } catch (err) {
                    errEl.textContent = err.message || 'Login failed';
                    errEl.style.display = 'block';
                }
            });
        }

        // Register Form Submit
        const registerForm = document.getElementById('registerForm');
        if (registerForm) {
            registerForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                const data = {
                    full_name: document.getElementById('reg-fullname').value,
                    email: document.getElementById('reg-email').value,
                    password: document.getElementById('reg-password').value,
                    role: document.getElementById('reg-role').value,
                    department: document.getElementById('reg-dept').value,
                    phone: document.getElementById('reg-phone').value
                };
                const errEl = document.getElementById('register-error');
                errEl.style.display = 'none';

                try {
                    const res = await FinTrustAPI.auth.register(data);
                    if (res.success) {
                        FinTrustAPI.setToken(res.token);
                        FinTrustAPI.setUser(res.user);
                        currentUser = res.user;
                        updateUIForUser(currentUser);
                        window.dispatchEvent(new CustomEvent('fintrust:user-authenticated', { detail: currentUser }));
                    }
                } catch (err) {
                    errEl.textContent = err.message || 'Registration failed';
                    errEl.style.display = 'block';
                }
            });
        }

        // Quick Demo Login Buttons
        document.querySelectorAll('.btn-quick-login').forEach(btn => {
            btn.addEventListener('click', async () => {
                const email = btn.getAttribute('data-email');
                document.getElementById('login-email').value = email;
                document.getElementById('login-password').value = 'Admin@123';
                document.getElementById('loginForm').dispatchEvent(new Event('submit'));
            });
        });

        // Logout
        const logoutBtn = document.getElementById('btn-logout');
        if (logoutBtn) {
            logoutBtn.addEventListener('click', () => {
                FinTrustAPI.removeToken();
                localStorage.removeItem('fintrust_user');
                currentUser = null;
                showLoginModal();
            });
        }

        // Listen for session expiry
        window.addEventListener('fintrust:auth-expired', () => {
            showLoginModal();
        });
    }

    return {
        init,
        getCurrentUser: () => currentUser,
        showLoginModal
    };
})();
