// ponytail: price lives here until a real billing provider owns it.
const PREMIUM_PRICE = '$5 / month';

// State
const state = {
    user: null,          // null = visitor, object = logged in
    urls: [],
    stats: { totalUrls: 0, totalClicks: 0, urlsCreatedToday: 0 },
    analytics: null,     // last link analytics, kept so charts can redraw on theme change
    searchQuery: '',
    apiBase: ''          // same origin
};

const $ = (id) => document.getElementById(id);

// Escape anything user-supplied before it goes into innerHTML.
const esc = (v) => String(v ?? '').replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const els = {
    viewHome: $('view-home'),
    viewBilling: $('view-billing'),

    themeToggle: $('theme-toggle'),
    navAnon: $('nav-anon'),
    navUser: $('nav-user'),
    navUserEmail: $('nav-user-email'),
    navPlanBadge: $('nav-plan-badge'),
    navUpgradeBtn: $('nav-upgrade-btn'),
    navLoginBtn: $('nav-login-btn'),
    navRegisterBtn: $('nav-register-btn'),
    navLogoutBtn: $('nav-logout-btn'),

    hero: $('hero'),
    pricing: $('pricing'),
    statsRow: $('stats-row'),
    statTotalUrls: $('stat-total-urls'),
    statTotalClicks: $('stat-total-clicks'),
    statUrlsToday: $('stat-urls-today'),

    chartsCard: $('charts-card'),
    chartsPill: $('charts-pill'),
    chartsGrid: $('charts-grid'),
    chartsUpsell: $('charts-upsell'),

    createForm: $('create-url-form'),
    urlInput: $('url-input'),
    aliasField: $('alias-field'),
    aliasInput: $('alias-input'),
    aliasPrefix: $('alias-prefix'),
    aliasPill: $('alias-pill'),
    aliasHint: $('alias-hint'),
    expiryInput: $('expiry-input'),
    submitBtn: $('submit-btn'),
    advancedToggle: $('advanced-toggle'),
    advancedOptions: $('advanced-options'),

    linksNote: $('links-note'),
    searchInput: $('search-input'),
    urlTableBody: $('url-table-body'),
    emptyState: $('empty-state'),
    tableLoader: $('table-loader'),

    billingCurrent: $('billing-current'),
    billingNote: $('billing-note'),
    freeState: $('free-state'),
    premiumState: $('premium-state'),
    checkoutBtn: $('checkout-btn'),

    qrModal: $('qr-modal'),
    qrImage: $('qr-image'),
    qrLoader: $('qr-loader'),
    qrShortUrl: $('qr-short-url'),
    downloadQrBtn: $('download-qr-btn'),

    analyticsModal: $('analytics-modal'),
    analyticsShortUrl: $('analytics-short-url'),
    analyticsTotalClicks: $('analytics-total-clicks'),
    analyticsLoader: $('analytics-loader'),
    analyticsContent: $('analytics-content'),

    loginModal: $('login-modal'),
    loginForm: $('login-form'),
    loginEmail: $('login-email'),
    loginPassword: $('login-password'),
    loginSubmit: $('login-submit'),

    registerModal: $('register-modal'),
    registerForm: $('register-form'),
    registerEmail: $('register-email'),
    registerPassword: $('register-password'),
    registerSubmit: $('register-submit'),

    toastContainer: $('toast-container'),
    modals: document.querySelectorAll('.modal-backdrop'),
    modalCloses: document.querySelectorAll('.modal-close')
};

// API
const api = {
    async getMe() {
        try {
            const res = await fetch(`${state.apiBase}/api/me`, { credentials: 'include' });
            if (!res.ok) return null;
            return await res.json();
        } catch (e) {
            return null;
        }
    },
    async login(email, password) {
        const res = await fetch(`${state.apiBase}/api/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password }),
            credentials: 'include'
        });
        if (!res.ok) throw new Error('Login failed');
        return await res.json();
    },
    async register(email, password) {
        const res = await fetch(`${state.apiBase}/api/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password }),
            credentials: 'include'
        });
        if (!res.ok) throw new Error('Registration failed');
        return await res.json();
    },
    async logout() {
        const res = await fetch(`${state.apiBase}/api/auth/logout`, {
            method: 'POST',
            credentials: 'include'
        });
        if (!res.ok) throw new Error('Logout failed');
        return true;
    },
    async upgrade() {
        const res = await fetch(`${state.apiBase}/api/me/upgrade`, {
            method: 'POST',
            credentials: 'include'
        });
        if (!res.ok) {
            // 404 = the server has mock upgrades disabled, i.e. no payment path exists yet.
            const err = new Error('Upgrade failed');
            err.status = res.status;
            throw err;
        }
        return await res.json();
    },
    async fetchDashboardStats() {
        if (!state.user) return null;
        try {
            const res = await fetch(`${state.apiBase}/api/analytics/dashboard`, { credentials: 'include' });
            if (!res.ok) throw new Error('Failed to fetch stats');
            return await res.json();
        } catch (e) {
            console.error(e);
            return null;
        }
    },
    async fetchUrls() {
        if (state.user) {
            try {
                const res = await fetch(`${state.apiBase}/api/urls`, { credentials: 'include' });
                if (!res.ok) throw new Error('Failed to fetch URLs');
                return await res.json();
            } catch (e) {
                showToast('Failed to load URLs', 'error');
                return [];
            }
        }
        // Anonymous: one lookup per stored stats token; 404 means the link is gone.
        const tokens = JSON.parse(localStorage.getItem('cuturl_stats_tokens') || '{}');
        const urls = (await Promise.all(Object.keys(tokens).map(async shortCode => {
            const res = await fetch(`${state.apiBase}/api/urls/${shortCode}`, {
                headers: { 'X-Stats-Token': tokens[shortCode] }
            }).catch(() => null);
            if (res && res.ok) return await res.json();
            if (res && res.status === 404) delete tokens[shortCode];
            return null;
        }))).filter(Boolean);
        localStorage.setItem('cuturl_stats_tokens', JSON.stringify(tokens));
        return urls;
    },
    async createUrl(data) {
        const res = await fetch(`${state.apiBase}/api/urls`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data),
            credentials: 'include'
        });
        if (!res.ok) {
            const errorData = await res.json().catch(() => ({}));
            throw new Error(errorData.message || 'Failed to create short link');
        }
        const created = await res.json();
        if (!state.user && created.statsToken) {
            const tokens = JSON.parse(localStorage.getItem('cuturl_stats_tokens') || '{}');
            tokens[created.shortCode] = created.statsToken;
            localStorage.setItem('cuturl_stats_tokens', JSON.stringify(tokens));
        }
        return created;
    },
    async deleteUrl(shortCode) {
        const tokens = state.user
            ? null
            : JSON.parse(localStorage.getItem('cuturl_stats_tokens') || '{}');
        const headers = tokens && tokens[shortCode]
            ? { 'X-Stats-Token': tokens[shortCode] }
            : {};
        const res = await fetch(`${state.apiBase}/api/urls/${shortCode}`, {
            method: 'DELETE',
            headers,
            credentials: 'include'
        });
        if (!res.ok) throw new Error('Failed to delete URL');
        if (tokens) {
            delete tokens[shortCode];
            localStorage.setItem('cuturl_stats_tokens', JSON.stringify(tokens));
        }
        return true;
    },
    async fetchAnalytics(shortCode) {
        // Authenticated + premium + owner only; no stats-token path exists here.
        const res = await fetch(`${state.apiBase}/api/analytics/${shortCode}`, {
            credentials: 'include'
        });
        if (!res.ok) throw new Error('Failed to fetch analytics');
        return await res.json();
    }
};

// Init
document.addEventListener('DOMContentLoaded', () => {
    init();
    // Visitors have nothing account-wide to poll, and each poll costs one request per stored token.
    setInterval(() => { if (state.user) refreshData(); }, 30000);
});

async function init() {
    els.aliasPrefix.innerText = `${location.host}/`;
    document.querySelectorAll('[data-price]').forEach(el => { el.innerText = PREMIUM_PRICE; });

    state.user = await api.getMe();
    updateAuthUI();
    setupEventListeners();
    route();
    await refreshData();
}

/* Views: home (visitor or account) and billing. Hash-routed, so it survives a refresh. */
function route() {
    const wantsBilling = location.hash === '#billing';

    // Billing is an account page. Visitors get the signup prompt instead.
    if (wantsBilling && !state.user) {
        els.registerModal.classList.add('show');
        location.hash = '#';
        return;
    }

    els.viewHome.classList.toggle('hidden', wantsBilling);
    els.viewBilling.classList.toggle('hidden', !wantsBilling);
    if (wantsBilling) renderBilling();
    window.scrollTo({ top: 0 });
}

function updateAuthUI() {
    const user = state.user;
    const premium = !!(user && user.premium);

    els.navAnon.classList.toggle('hidden', !!user);
    els.navUser.classList.toggle('hidden', !user);
    els.navUpgradeBtn.classList.toggle('hidden', !user || premium);

    if (user) {
        els.navUserEmail.innerText = user.email;
        els.navPlanBadge.innerText = premium ? 'Premium' : 'Free';
        els.navPlanBadge.className = premium ? 'plan-badge premium' : 'plan-badge';
    }

    // A visitor sees the tool, their own links and the plan comparison — and no premium
    // control at all: no account stats, no charts panel, no alias field, no QR/analytics
    // buttons (see renderUrls). The upsell only appears once there is an account to upgrade.
    els.viewHome.classList.toggle('anon', !user);
    els.hero.classList.toggle('hidden', !!user);
    els.pricing.classList.toggle('hidden', !!user);
    els.statsRow.classList.toggle('hidden', !user);
    els.chartsCard.classList.toggle('hidden', !user);

    els.aliasField.classList.toggle('hidden', !user);
    els.aliasInput.disabled = !premium;
    els.aliasPill.classList.toggle('hidden', premium);
    els.aliasHint.classList.toggle('hidden', premium);

    els.linksNote.innerText = user
        ? 'Links on your account.'
        : 'Saved in this browser only — create an account to keep them anywhere.';
}

async function refreshData() {
    els.tableLoader.classList.remove('hidden');

    // fetchDashboardStats already returns null when anonymous.
    const [stats, urls] = await Promise.all([api.fetchDashboardStats(), api.fetchUrls()]);

    state.stats = stats || { totalUrls: 0, totalClicks: 0, urlsCreatedToday: 0 };
    renderStats();
    if (stats) renderDashboardCharts(stats);

    if (urls) {
        state.urls = urls;
        renderUrls();
    }

    els.tableLoader.classList.add('hidden');
}

// Rendering
function animateValue(el, end) {
    if (!el) return;
    const start = parseInt(el.innerText.replace(/,/g, ''), 10) || 0;
    if (start === end) return;
    const startedAt = performance.now();
    const step = (now) => {
        const p = Math.min((now - startedAt) / 600, 1);
        el.innerText = Math.round(start + (end - start) * p).toLocaleString();
        if (p < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
}

function renderStats() {
    animateValue(els.statTotalUrls, state.stats.totalUrls || 0);
    animateValue(els.statTotalClicks, state.stats.totalClicks || 0);
    animateValue(els.statUrlsToday, state.stats.urlsCreatedToday || 0);
}

function renderDashboardCharts(stats) {
    const premium = !!(state.user && state.user.premium);

    els.chartsPill.classList.toggle('hidden', premium);
    els.chartsGrid.classList.toggle('hidden', !premium);
    els.chartsUpsell.classList.toggle('hidden', premium);
    if (!premium) return;

    drawLineChart('dash-line-chart-canvas',
        stats.clicksByDate && stats.clicksByDate.length ? stats.clicksByDate : []);
    drawPieChart('dash-browser-chart-canvas', stats.browserStats || {});
}

function renderUrls() {
    const q = state.searchQuery.toLowerCase();
    const rows = state.urls
        .filter(u => u.shortCode.toLowerCase().includes(q) || u.originalUrl.toLowerCase().includes(q))
        .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

    els.urlTableBody.innerHTML = '';
    els.emptyState.classList.toggle('hidden', rows.length > 0);
    els.emptyState.innerText = state.searchQuery ? 'Nothing matches that search.' : 'No links yet.';
    if (!rows.length) return;

    // QR and per-link analytics are premium. Visitors get no such button at all;
    // free accounts get a locked one that leads to the plans page.
    const premium = !!(state.user && state.user.premium);
    const showPremiumActions = !!state.user;

    rows.forEach(url => {
        const full = url.shortUrl || `${location.origin}/${url.shortCode}`;
        const created = new Date(url.createdAt)
            .toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
        const expired = url.expiresAt && new Date(url.expiresAt) < new Date();

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>
                <div class="cell-link">
                    <a href="/${esc(url.shortCode)}" target="_blank" rel="noopener" class="code">/${esc(url.shortCode)}</a>
                    ${expired ? '<span class="badge expired">Expired</span>' : ''}
                    <button class="icon-btn copy-btn" data-url="${esc(full)}" title="Copy link" aria-label="Copy link">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="12" height="12" rx="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                    </button>
                </div>
            </td>
            <td><a href="${esc(url.originalUrl)}" target="_blank" rel="noopener" class="dest" title="${esc(url.originalUrl)}">${esc(url.originalUrl)}</a></td>
            <td class="muted">${created}</td>
            <td class="num">${Number(url.clickCount) || 0}</td>
            <td>
                <div class="row-actions">
                    ${showPremiumActions ? `
                    <button class="icon-btn qr-action${premium ? '' : ' locked'}" data-code="${esc(url.shortCode)}" data-url="${esc(full)}" title="${premium ? 'QR code' : 'QR codes are a Premium feature'}" aria-label="QR code">
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"></rect><rect x="14" y="3" width="7" height="7"></rect><rect x="3" y="14" width="7" height="7"></rect><rect x="14" y="14" width="7" height="7"></rect></svg>
                    </button>
                    <button class="icon-btn analytics-action${premium ? '' : ' locked'}" data-code="${esc(url.shortCode)}" data-url="${esc(full)}" title="${premium ? 'Analytics' : 'Analytics is a Premium feature'}" aria-label="Analytics">
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="20" x2="18" y2="10"></line><line x1="12" y1="20" x2="12" y2="4"></line><line x1="6" y1="20" x2="6" y2="14"></line></svg>
                    </button>` : ''}
                    <button class="icon-btn danger delete-action" data-code="${esc(url.shortCode)}" title="Delete" aria-label="Delete">
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
                    </button>
                </div>
            </td>`;
        els.urlTableBody.appendChild(tr);
    });
}

function renderBilling() {
    const user = state.user;
    if (!user) return;
    const premium = !!user.premium;

    const until = user.planExpiresAt
        ? ` until ${new Date(user.planExpiresAt).toLocaleDateString(undefined, { dateStyle: 'medium' })}`
        : '';
    els.billingCurrent.innerText = premium
        ? `You're on Premium${until}.`
        : "You're on the Free plan.";

    els.freeState.innerText = premium ? '' : 'Current plan';
    els.premiumState.innerText = premium ? 'Current plan' : '';
    els.checkoutBtn.classList.toggle('hidden', premium);
    els.billingNote.innerText = premium
        ? 'Premium is active on this account. Cancelling is not available yet — get in touch and we will sort it out.'
        : 'Card payments are not live yet. Premium can be switched on for your account while we finish setting billing up.';
}

// Interactions
function setupEventListeners() {
    window.addEventListener('hashchange', route);

    els.themeToggle.addEventListener('click', () => {
        const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
        document.documentElement.dataset.theme = next;
        try { localStorage.setItem('cuturl_theme', next); } catch (e) { /* storage blocked */ }
        redrawCharts();  // canvas colours are read from CSS tokens at draw time
    });

    let resizeTimer;
    window.addEventListener('resize', () => {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(redrawCharts, 150);
    });

    // Auth
    els.navLoginBtn.addEventListener('click', () => els.loginModal.classList.add('show'));
    els.navRegisterBtn.addEventListener('click', () => els.registerModal.classList.add('show'));
    document.querySelectorAll('[data-open-register]').forEach(b =>
        b.addEventListener('click', () => els.registerModal.classList.add('show')));

    els.navLogoutBtn.addEventListener('click', async () => {
        try {
            await api.logout();
            state.user = null;
            location.hash = '#';
            updateAuthUI();
            route();
            await refreshData();
            showToast('Logged out');
        } catch (e) {
            showToast('Failed to log out', 'error');
        }
    });

    $('switch-to-register').addEventListener('click', (e) => {
        e.preventDefault();
        els.loginModal.classList.remove('show');
        els.registerModal.classList.add('show');
    });
    $('switch-to-login').addEventListener('click', (e) => {
        e.preventDefault();
        els.registerModal.classList.remove('show');
        els.loginModal.classList.add('show');
    });

    els.loginForm.addEventListener('submit', (e) => submitAuth(e, els.loginSubmit, els.loginModal,
        () => api.login(els.loginEmail.value, els.loginPassword.value), 'Logged in', 'Login failed'));

    els.registerForm.addEventListener('submit', (e) => submitAuth(e, els.registerSubmit, els.registerModal,
        // /register logs the new account in and returns it.
        () => api.register(els.registerEmail.value, els.registerPassword.value),
        'Account created', 'Registration failed'));

    // Upgrading always goes through the plans page — never straight to the upgrade call.
    els.navUpgradeBtn.addEventListener('click', () => { location.hash = '#billing'; });
    document.querySelectorAll('[data-goto-billing]').forEach(b =>
        b.addEventListener('click', () => { location.hash = '#billing'; }));
    document.querySelectorAll('[data-goto-home]').forEach(b =>
        b.addEventListener('click', () => { location.hash = '#'; }));

    els.checkoutBtn.addEventListener('click', async () => {
        setBtnLoading(els.checkoutBtn, true);
        try {
            state.user = await api.upgrade();
            updateAuthUI();
            renderBilling();
            await refreshData();
            showToast('Premium is active on your account', 'success');
        } catch (err) {
            showToast(err.status === 404
                ? 'Payments are not live on this server yet.'
                : 'Upgrade failed. Please try again.', 'error');
        } finally {
            setBtnLoading(els.checkoutBtn, false);
        }
    });

    // Create form
    els.advancedToggle.addEventListener('click', () => {
        const open = els.advancedOptions.classList.toggle('open');
        els.advancedToggle.setAttribute('aria-expanded', String(open));
    });

    els.createForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const payload = { url: els.urlInput.value.trim() };

        const alias = els.aliasInput.value.trim();
        if (alias && !els.aliasInput.disabled) payload.customAlias = alias;
        if (els.expiryInput.value) payload.expiresAt = new Date(els.expiryInput.value).toISOString();

        setBtnLoading(els.submitBtn, true);
        try {
            const created = await api.createUrl(payload);
            state.urls.unshift(created);
            state.stats.totalUrls++;
            state.stats.urlsCreatedToday++;
            renderStats();
            renderUrls();

            els.createForm.reset();
            els.advancedOptions.classList.remove('open');
            els.advancedToggle.setAttribute('aria-expanded', 'false');

            await copy(created.shortUrl || `${location.origin}/${created.shortCode}`);
            showToast('Link created and copied', 'success');
        } catch (err) {
            showToast(err.message || 'Could not create the link', 'error');
        } finally {
            setBtnLoading(els.submitBtn, false);
        }
    });

    els.searchInput.addEventListener('input', (e) => {
        state.searchQuery = e.target.value;
        renderUrls();
    });

    // Table actions
    els.urlTableBody.addEventListener('click', async (e) => {
        const btn = e.target.closest('button');
        if (!btn) return;

        if (btn.classList.contains('locked')) {
            location.hash = '#billing';
            return;
        }

        if (btn.classList.contains('copy-btn')) {
            await copy(btn.dataset.url);
            showToast('Copied', 'success');
            return;
        }
        if (btn.classList.contains('qr-action')) {
            openQrModal(btn.dataset.code, btn.dataset.url);
            return;
        }
        if (btn.classList.contains('analytics-action')) {
            openAnalyticsModal(btn.dataset.code, btn.dataset.url);
            return;
        }
        if (btn.classList.contains('delete-action')) {
            if (!confirm('Delete this link? Anyone using it will get a 404.')) return;
            try {
                await api.deleteUrl(btn.dataset.code);
                state.urls = state.urls.filter(u => u.shortCode !== btn.dataset.code);
                state.stats.totalUrls = Math.max(0, state.stats.totalUrls - 1);
                renderStats();
                renderUrls();
                showToast('Link deleted', 'success');
            } catch (err) {
                showToast('Could not delete the link', 'error');
            }
        }
    });

    // Modals
    els.modalCloses.forEach(btn =>
        btn.addEventListener('click', () => closeModals()));
    els.modals.forEach(modal =>
        modal.addEventListener('click', (e) => { if (e.target === modal) closeModals(); }));
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeModals(); });

    els.downloadQrBtn.addEventListener('click', () => {
        if (!els.qrImage.src) return;
        const a = document.createElement('a');
        a.href = els.qrImage.src;
        a.download = `qr-${els.qrShortUrl.innerText.split('/').pop() || 'code'}.png`;
        a.click();
    });
}

async function submitAuth(e, btn, modal, call, okMsg, failMsg) {
    e.preventDefault();
    setBtnLoading(btn, true);
    try {
        state.user = await call();
        updateAuthUI();
        modal.classList.remove('show');
        showToast(okMsg, 'success');
        await refreshData();
    } catch (err) {
        showToast(failMsg, 'error');
    } finally {
        setBtnLoading(btn, false);
    }
}

function closeModals() {
    els.modals.forEach(m => m.classList.remove('show'));
}

async function copy(text) {
    try {
        await navigator.clipboard.writeText(text);
        return true;
    } catch (e) {
        return false;   // insecure context or denied permission; the link is on screen anyway
    }
}

function setBtnLoading(btn, isLoading) {
    if (!btn) return;
    btn.disabled = isLoading;
    const loader = btn.querySelector('.btn-loader');
    if (loader) loader.classList.toggle('hidden', !isLoading);
}

function openQrModal(shortCode, fullUrl) {
    els.qrModal.classList.add('show');
    els.qrShortUrl.innerText = fullUrl;
    els.qrImage.classList.remove('loaded');
    els.qrLoader.classList.remove('hidden');

    els.qrImage.src = `${state.apiBase}/api/qr/${shortCode}?width=300&height=300`;
    els.qrImage.onload = () => {
        els.qrLoader.classList.add('hidden');
        els.qrImage.classList.add('loaded');
    };
    els.qrImage.onerror = () => {
        els.qrLoader.classList.add('hidden');
        closeModals();
        showToast('Could not load the QR code', 'error');
    };
}

async function openAnalyticsModal(shortCode, fullUrl) {
    els.analyticsModal.classList.add('show');
    els.analyticsShortUrl.innerText = fullUrl;
    els.analyticsShortUrl.href = fullUrl;
    els.analyticsContent.classList.add('hidden');
    els.analyticsLoader.classList.remove('hidden');

    try {
        const data = await api.fetchAnalytics(shortCode);
        state.analytics = data;

        els.analyticsTotalClicks.innerText = `${data.totalClicks || 0} clicks`;
        els.analyticsLoader.classList.add('hidden');
        els.analyticsContent.classList.remove('hidden');
        drawAnalyticsCharts(data);
    } catch (err) {
        els.analyticsLoader.classList.add('hidden');
        closeModals();
        showToast('Could not load analytics', 'error');
    }
}

function drawAnalyticsCharts(data) {
    drawLineChart('line-chart-canvas', data.clicksByDate || []);
    drawPieChart('browser-chart-canvas', data.browserStats || {});
    drawPieChart('os-chart-canvas', data.osStats || {});
}

// Canvas colours come from CSS tokens, so every visible chart is redrawn on theme change.
function redrawCharts() {
    if (state.user && state.user.premium && state.stats) renderDashboardCharts(state.stats);
    if (state.analytics && els.analyticsModal.classList.contains('show')) {
        drawAnalyticsCharts(state.analytics);
    }
}

function showToast(message, type = 'success') {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = type === 'error'
        ? '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg><span></span>'
        : '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg><span></span>';
    toast.querySelector('span').textContent = message;

    els.toastContainer.appendChild(toast);
    setTimeout(() => {
        toast.classList.add('hiding');
        toast.addEventListener('animationend', () => toast.remove());
    }, 3200);
}

/* ===================== Charts (plain canvas) ===================== */
const token = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();

// Size the canvas in device pixels so lines stay crisp on hi-dpi screens.
function prepCanvas(canvasId) {
    const canvas = document.getElementById(canvasId);
    if (!canvas || !canvas.parentElement.clientWidth) return null;

    const box = canvas.parentElement;
    const dpr = window.devicePixelRatio || 1;
    const width = box.clientWidth;
    const height = box.clientHeight;

    canvas.width = width * dpr;
    canvas.height = height * dpr;
    canvas.style.width = '100%';
    canvas.style.height = '100%';

    const ctx = canvas.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, width, height);
    ctx.font = '11px Inter, system-ui, sans-serif';
    return { ctx, width, height };
}

function noData(ctx, width, height) {
    ctx.fillStyle = token('--text-dim');
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('No data yet', width / 2, height / 2);
}

function drawLineChart(canvasId, points) {
    const c = prepCanvas(canvasId);
    if (!c) return;
    const { ctx, width, height } = c;
    if (!points.length) return noData(ctx, width, height);

    const pad = { top: 14, right: 12, bottom: 24, left: 34 };
    const plotW = width - pad.left - pad.right;
    const plotH = height - pad.top - pad.bottom;
    const max = Math.max(...points.map(p => p.count), 4);
    const x = (i) => pad.left + (points.length === 1 ? plotW / 2 : (i / (points.length - 1)) * plotW);
    const y = (v) => pad.top + plotH - (v / max) * plotH;

    // Grid + y labels
    ctx.strokeStyle = token('--chart-grid');
    ctx.fillStyle = token('--text-dim');
    ctx.lineWidth = 1;
    ctx.textAlign = 'right';
    ctx.textBaseline = 'middle';
    for (let i = 0; i <= 4; i++) {
        const gy = Math.round(y((max / 4) * i)) + 0.5;
        ctx.beginPath();
        ctx.moveTo(pad.left, gy);
        ctx.lineTo(width - pad.right, gy);
        ctx.stroke();
        ctx.fillText(Math.round((max / 4) * i), pad.left - 7, gy);
    }

    const accent = token('--chart-1');

    // Area under the line
    ctx.beginPath();
    ctx.moveTo(x(0), y(0));
    points.forEach((p, i) => ctx.lineTo(x(i), y(p.count)));
    ctx.lineTo(x(points.length - 1), y(0));
    ctx.closePath();
    ctx.globalAlpha = 0.14;
    ctx.fillStyle = accent;
    ctx.fill();
    ctx.globalAlpha = 1;

    // Line
    ctx.beginPath();
    points.forEach((p, i) => i ? ctx.lineTo(x(i), y(p.count)) : ctx.moveTo(x(i), y(p.count)));
    ctx.strokeStyle = accent;
    ctx.lineWidth = 2;
    ctx.lineJoin = 'round';
    ctx.stroke();

    // Points + sparse x labels
    const every = Math.ceil(points.length / 6);
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    points.forEach((p, i) => {
        ctx.beginPath();
        ctx.arc(x(i), y(p.count), 3, 0, Math.PI * 2);
        ctx.fillStyle = token('--surface-2');
        ctx.fill();
        ctx.strokeStyle = accent;
        ctx.lineWidth = 2;
        ctx.stroke();

        if (i % every === 0 || i === points.length - 1) {
            ctx.fillStyle = token('--text-dim');
            ctx.fillText(shortDate(p.date), x(i), height - pad.bottom + 8);
        }
    });
}

// '2026-08-31' -> '31 Aug'; anything else is passed through.
function shortDate(value) {
    const d = new Date(value);
    return isNaN(d) ? String(value ?? '')
        : d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
}

function drawPieChart(canvasId, dataMap) {
    const c = prepCanvas(canvasId);
    if (!c) return;
    const { ctx, width, height } = c;

    const entries = Object.entries(dataMap).sort((a, b) => b[1] - a[1]).slice(0, 5);
    if (!entries.length) return noData(ctx, width, height);

    const total = entries.reduce((sum, [, n]) => sum + n, 0);
    const colors = [1, 2, 3, 4, 5].map(i => token(`--chart-${i}`));
    const radius = Math.min(height / 2 - 6, width / 4);
    const cx = radius + 8;
    const cy = height / 2;

    let angle = -Math.PI / 2;
    entries.forEach(([label, n], i) => {
        const slice = (n / total) * Math.PI * 2;

        ctx.beginPath();
        ctx.moveTo(cx, cy);
        ctx.arc(cx, cy, radius, angle, angle + slice);
        ctx.closePath();
        ctx.fillStyle = colors[i];
        ctx.fill();
        ctx.strokeStyle = token('--surface-2');
        ctx.lineWidth = 2;
        ctx.stroke();
        angle += slice;

        // Legend
        const ly = cy - radius + 10 + i * 18;
        ctx.beginPath();
        ctx.arc(cx + radius + 16, ly, 4, 0, Math.PI * 2);
        ctx.fillStyle = colors[i];
        ctx.fill();

        ctx.fillStyle = token('--text');
        ctx.textAlign = 'left';
        ctx.textBaseline = 'middle';
        const text = `${label} ${Math.round((n / total) * 100)}%`;
        ctx.fillText(text.length > 18 ? `${text.slice(0, 17)}…` : text, cx + radius + 26, ly);
    });

    // Donut hole
    ctx.beginPath();
    ctx.arc(cx, cy, radius * 0.58, 0, Math.PI * 2);
    ctx.fillStyle = token('--surface-2');
    ctx.fill();
}
