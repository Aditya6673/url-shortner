// State Management
const state = {
    urls: [],
    stats: { totalUrls: 0, totalClicks: 0, urlsCreatedToday: 0 },
    searchQuery: '',
    apiBase: '' // Same origin
};

// DOM Elements
const els = {
    statTotalUrls: document.getElementById('stat-total-urls'),
    statTotalClicks: document.getElementById('stat-total-clicks'),
    statUrlsToday: document.getElementById('stat-urls-today'),
    
    createForm: document.getElementById('create-url-form'),
    urlInput: document.getElementById('url-input'),
    aliasInput: document.getElementById('alias-input'),
    expiryInput: document.getElementById('expiry-input'),
    submitBtn: document.getElementById('submit-btn'),
    
    advancedToggle: document.getElementById('advanced-toggle'),
    advancedOptions: document.getElementById('advanced-options'),
    
    searchInput: document.getElementById('search-input'),
    urlTableBody: document.getElementById('url-table-body'),
    emptyState: document.getElementById('empty-state'),
    tableLoader: document.getElementById('table-loader'),
    
    qrModal: document.getElementById('qr-modal'),
    qrImage: document.getElementById('qr-image'),
    qrLoader: document.getElementById('qr-loader'),
    qrShortUrl: document.getElementById('qr-short-url'),
    downloadQrBtn: document.getElementById('download-qr-btn'),
    
    analyticsModal: document.getElementById('analytics-modal'),
    analyticsShortUrl: document.getElementById('analytics-short-url'),
    analyticsTotalClicks: document.getElementById('analytics-total-clicks'),
    analyticsLoader: document.getElementById('analytics-loader'),
    analyticsContent: document.getElementById('analytics-content'),
    
    toastContainer: document.getElementById('toast-container'),
    modals: document.querySelectorAll('.modal-backdrop'),
    modalCloses: document.querySelectorAll('.modal-close')
};

// API Services
const api = {
    async fetchDashboardStats() {
        try {
            const res = await fetch(`${state.apiBase}/api/analytics/dashboard`);
            if (!res.ok) throw new Error('Failed to fetch stats');
            return await res.json();
        } catch (e) {
            console.error(e);
            return null;
        }
    },
    async fetchUrls() {
        try {
            const res = await fetch(`${state.apiBase}/api/urls`);
            if (!res.ok) throw new Error('Failed to fetch URLs');
            return await res.json();
        } catch (e) {
            showToast('Failed to load URLs', 'error');
            return [];
        }
    },
    async createUrl(data) {
        try {
            const res = await fetch(`${state.apiBase}/api/urls`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            if (!res.ok) {
                const errorData = await res.json().catch(() => ({}));
                throw new Error(errorData.message || 'Failed to create short link');
            }
            return await res.json();
        } catch (e) {
            throw e;
        }
    },
    async deleteUrl(id) {
        try {
            const res = await fetch(`${state.apiBase}/api/urls/${id}`, { method: 'DELETE' });
            if (!res.ok) throw new Error('Failed to delete URL');
            return true;
        } catch (e) {
            throw e;
        }
    },
    async fetchAnalytics(shortCode) {
        try {
            const res = await fetch(`${state.apiBase}/api/analytics/${shortCode}`);
            if (!res.ok) throw new Error('Failed to fetch analytics');
            return await res.json();
        } catch (e) {
            throw e;
        }
    }
};

// Initialization
document.addEventListener('DOMContentLoaded', () => {
    init();
    setInterval(refreshData, 30000); // Auto-refresh every 30s
});

async function init() {
    setupEventListeners();
    await refreshData();
}

async function refreshData() {
    els.tableLoader.classList.remove('hidden');
    
    const [stats, urls] = await Promise.all([
        api.fetchDashboardStats(),
        api.fetchUrls()
    ]);
    
    if (stats) {
        state.stats = stats;
        renderStats();
    }
    
    if (urls) {
        state.urls = urls;
        renderUrls();
    }
    
    els.tableLoader.classList.add('hidden');
}

// UI Rendering
function animateValue(obj, start, end, duration) {
    let startTimestamp = null;
    const step = (timestamp) => {
        if (!startTimestamp) startTimestamp = timestamp;
        const progress = Math.min((timestamp - startTimestamp) / duration, 1);
        obj.innerHTML = Math.floor(progress * (end - start) + start).toLocaleString();
        if (progress < 1) {
            window.requestAnimationFrame(step);
        } else {
            obj.innerHTML = end.toLocaleString();
        }
    };
    window.requestAnimationFrame(step);
}

function renderStats() {
    animateValue(els.statTotalUrls, parseInt(els.statTotalUrls.innerText.replace(/,/g, '')) || 0, state.stats.totalUrls || 0, 1000);
    animateValue(els.statTotalClicks, parseInt(els.statTotalClicks.innerText.replace(/,/g, '')) || 0, state.stats.totalClicks || 0, 1000);
    animateValue(els.statUrlsToday, parseInt(els.statUrlsToday.innerText.replace(/,/g, '')) || 0, state.stats.urlsCreatedToday || 0, 1000);
}

function renderUrls() {
    els.urlTableBody.innerHTML = '';
    
    const filteredUrls = state.urls.filter(url => 
        url.shortCode.toLowerCase().includes(state.searchQuery.toLowerCase()) || 
        url.originalUrl.toLowerCase().includes(state.searchQuery.toLowerCase())
    );
    
    if (filteredUrls.length === 0) {
        els.emptyState.classList.remove('hidden');
    } else {
        els.emptyState.classList.add('hidden');
        
        filteredUrls.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)).forEach(url => {
            const tr = document.createElement('tr');
            
            const date = new Date(url.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
            const isActive = url.active !== false; // Assume active by default if undefined
            
            tr.innerHTML = `
                <td>
                    <div class="short-link-cell">
                        <a href="${url.shortUrl || '/' + url.shortCode}" target="_blank" class="short-url">${url.shortCode}</a>
                        <button class="btn-icon copy-btn" data-url="${url.shortUrl || window.location.origin + '/' + url.shortCode}" title="Copy">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                        </button>
                    </div>
                </td>
                <td><a href="${url.originalUrl}" target="_blank" class="original-url" title="${url.originalUrl}">${url.originalUrl}</a></td>
                <td class="text-muted">${date}</td>
                <td>${url.clickCount || 0}</td>
                <td><span class="badge ${isActive ? 'active' : 'expired'}">${isActive ? 'Active' : 'Expired'}</span></td>
                <td>
                    <div class="actions-cell">
                        <button class="btn-icon qr-action" data-code="${url.shortCode}" data-url="${url.shortUrl || window.location.origin + '/' + url.shortCode}" title="QR Code">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"></rect><rect x="14" y="3" width="7" height="7"></rect><rect x="14" y="14" width="7" height="7"></rect><rect x="3" y="14" width="7" height="7"></rect></svg>
                        </button>
                        <button class="btn-icon analytics-action" data-code="${url.shortCode}" data-url="${url.shortUrl || window.location.origin + '/' + url.shortCode}" title="Analytics">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="20" x2="18" y2="10"></line><line x1="12" y1="20" x2="12" y2="4"></line><line x1="6" y1="20" x2="6" y2="14"></line></svg>
                        </button>
                        <button class="btn-icon btn-danger delete-action" data-id="${url.id}" title="Delete">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
                        </button>
                    </div>
                </td>
            `;
            els.urlTableBody.appendChild(tr);
        });
    }
}

// Interactions
function setupEventListeners() {
    // Advanced toggle
    els.advancedToggle.addEventListener('click', () => {
        els.advancedToggle.classList.toggle('open');
        els.advancedOptions.classList.toggle('open');
    });
    
    // Form submit
    els.createForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const url = els.urlInput.value.trim();
        if (!url) return;
        
        const payload = { url };
        
        const alias = els.aliasInput.value.trim();
        if (alias) payload.customAlias = alias;
        
        const expiry = els.expiryInput.value;
        if (expiry) payload.expiresAt = new Date(expiry).toISOString();
        
        setFormLoading(true);
        try {
            const newUrl = await api.createUrl(payload);
            state.urls.unshift(newUrl);
            state.stats.totalUrls++;
            state.stats.urlsCreatedToday++;
            renderStats();
            renderUrls();
            
            // Reset form
            els.urlInput.value = '';
            els.aliasInput.value = '';
            els.expiryInput.value = '';
            els.advancedToggle.classList.remove('open');
            els.advancedOptions.classList.remove('open');
            
            showToast('Link created successfully!', 'success');
        } catch (err) {
            showToast(err.message || 'Error creating link', 'error');
        } finally {
            setFormLoading(false);
        }
    });
    
    // Search
    els.searchInput.addEventListener('input', (e) => {
        state.searchQuery = e.target.value;
        renderUrls();
    });
    
    // Table actions (Delegation)
    els.urlTableBody.addEventListener('click', async (e) => {
        const target = e.target.closest('button');
        if (!target) return;
        
        // Copy action
        if (target.classList.contains('copy-btn')) {
            const urlToCopy = target.dataset.url;
            try {
                await navigator.clipboard.writeText(urlToCopy);
                
                // Visual feedback
                const originalHtml = target.innerHTML;
                target.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-success"><polyline points="20 6 9 17 4 12"></polyline></svg>';
                target.style.color = 'var(--success)';
                
                setTimeout(() => {
                    target.innerHTML = originalHtml;
                    target.style.color = '';
                }, 2000);
                
                showToast('Copied to clipboard!', 'success');
            } catch (err) {
                showToast('Failed to copy', 'error');
            }
        }
        
        // QR Action
        if (target.classList.contains('qr-action')) {
            const shortCode = target.dataset.code;
            const fullUrl = target.dataset.url;
            openQrModal(shortCode, fullUrl);
        }
        
        // Analytics Action
        if (target.classList.contains('analytics-action')) {
            const shortCode = target.dataset.code;
            const fullUrl = target.dataset.url;
            openAnalyticsModal(shortCode, fullUrl);
        }
        
        // Delete Action
        if (target.classList.contains('delete-action')) {
            if (confirm('Are you sure you want to delete this link?')) {
                const id = target.dataset.id;
                try {
                    await api.deleteUrl(id);
                    state.urls = state.urls.filter(u => u.id != id);
                    state.stats.totalUrls = Math.max(0, state.stats.totalUrls - 1);
                    renderStats();
                    renderUrls();
                    showToast('Link deleted', 'success');
                } catch (err) {
                    showToast('Failed to delete link', 'error');
                }
            }
        }
    });
    
    // Modals
    els.modalCloses.forEach(btn => {
        btn.addEventListener('click', () => {
            els.modals.forEach(m => m.classList.remove('show'));
        });
    });
    
    els.modals.forEach(modal => {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) modal.classList.remove('show');
        });
    });
    
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            els.modals.forEach(m => m.classList.remove('show'));
        }
    });
    
    // QR Download
    els.downloadQrBtn.addEventListener('click', () => {
        const imgSrc = els.qrImage.src;
        if (!imgSrc) return;
        
        const a = document.createElement('a');
        a.href = imgSrc;
        a.download = `qr-${els.qrShortUrl.innerText.split('/').pop() || 'code'}.png`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
    });
}

function setFormLoading(isLoading) {
    const text = els.submitBtn.querySelector('.btn-text');
    const loader = els.submitBtn.querySelector('.btn-loader');
    els.submitBtn.disabled = isLoading;
    
    if (isLoading) {
        loader.classList.remove('hidden');
    } else {
        loader.classList.add('hidden');
    }
}

function openQrModal(shortCode, fullUrl) {
    els.qrModal.classList.add('show');
    els.qrShortUrl.innerText = fullUrl;
    els.qrImage.classList.remove('loaded');
    els.qrLoader.classList.remove('hidden');
    
    const qrUrl = `${state.apiBase}/api/qr/${shortCode}?width=300&height=300`;
    els.qrImage.src = qrUrl;
    
    els.qrImage.onload = () => {
        els.qrLoader.classList.add('hidden');
        els.qrImage.classList.add('loaded');
    };
    
    els.qrImage.onerror = () => {
        els.qrLoader.classList.add('hidden');
        showToast('Failed to load QR code', 'error');
        els.qrModal.classList.remove('show');
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
        
        els.analyticsTotalClicks.innerText = `${data.totalClicks || 0} clicks`;
        els.analyticsLoader.classList.add('hidden');
        els.analyticsContent.classList.remove('hidden');
        
        // Draw charts
        if (data.clicksByDate && data.clicksByDate.length > 0) {
            drawLineChart('line-chart-canvas', data.clicksByDate);
        } else {
            // Draw empty line chart
            drawLineChart('line-chart-canvas', [{date: 'No Data', count: 0}]);
        }
        
        drawPieChart('browser-chart-canvas', data.browserStats || {});
        drawPieChart('os-chart-canvas', data.osStats || {});
        
    } catch (err) {
        els.analyticsLoader.classList.add('hidden');
        showToast('Failed to load analytics', 'error');
        els.analyticsModal.classList.remove('show');
    }
}

function showToast(message, type = 'success') {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    
    const icon = type === 'success' 
        ? '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>'
        : '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>';
    
    toast.innerHTML = `${icon}<span>${message}</span>`;
    els.toastContainer.appendChild(toast);
    
    setTimeout(() => {
        toast.classList.add('hiding');
        toast.addEventListener('animationend', () => toast.remove());
    }, 3000);
}

// Chart Rendering (Pure Canvas)
function drawLineChart(canvasId, dataList) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;
    
    // Handle container resize
    const container = canvas.parentElement;
    canvas.width = container.clientWidth;
    canvas.height = container.clientHeight;
    
    const ctx = canvas.getContext('2d');
    const width = canvas.width;
    const height = canvas.height;
    const padding = { top: 20, right: 20, bottom: 30, left: 40 };
    
    ctx.clearRect(0, 0, width, height);
    
    if (dataList.length === 0) return;
    
    // Calculate bounds
    const maxVal = Math.max(...dataList.map(d => d.count), 5); // Minimum y-axis of 5
    const minVal = 0;
    
    // Draw Grid and Y-axis labels
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.05)';
    ctx.lineWidth = 1;
    ctx.fillStyle = '#8892b0';
    ctx.font = '10px Inter, sans-serif';
    ctx.textAlign = 'right';
    ctx.textBaseline = 'middle';
    
    const ySteps = 5;
    for (let i = 0; i <= ySteps; i++) {
        const val = minVal + (maxVal - minVal) * (i / ySteps);
        const y = height - padding.bottom - (i / ySteps) * (height - padding.top - padding.bottom);
        
        ctx.beginPath();
        ctx.moveTo(padding.left, y);
        ctx.lineTo(width - padding.right, y);
        ctx.stroke();
        
        ctx.fillText(Math.round(val), padding.left - 10, y);
    }
    
    // Draw X-axis labels (sparse if many points)
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    const stepX = (width - padding.left - padding.right) / Math.max(1, dataList.length - 1);
    
    // Draw Area Fill
    ctx.beginPath();
    ctx.moveTo(padding.left, height - padding.bottom);
    
    dataList.forEach((d, i) => {
        const x = padding.left + i * stepX;
        const y = height - padding.bottom - (d.count / maxVal) * (height - padding.top - padding.bottom);
        ctx.lineTo(x, y);
    });
    
    ctx.lineTo(padding.left + (dataList.length - 1) * stepX, height - padding.bottom);
    ctx.closePath();
    
    const gradient = ctx.createLinearGradient(0, padding.top, 0, height - padding.bottom);
    gradient.addColorStop(0, 'rgba(67, 97, 238, 0.4)');
    gradient.addColorStop(1, 'rgba(67, 97, 238, 0.0)');
    ctx.fillStyle = gradient;
    ctx.fill();
    
    // Draw Line
    ctx.beginPath();
    ctx.strokeStyle = '#4361ee';
    ctx.lineWidth = 2;
    dataList.forEach((d, i) => {
        const x = padding.left + i * stepX;
        const y = height - padding.bottom - (d.count / maxVal) * (height - padding.top - padding.bottom);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
        
        // Draw some X labels
        if (dataList.length <= 7 || i % Math.ceil(dataList.length / 5) === 0) {
            // format date
            let label = d.date;
            if (label && label.length > 5) {
                const parts = label.split('-');
                if(parts.length >= 3) label = `${parts[1]}/${parts[2]}`;
            }
            ctx.fillStyle = '#8892b0';
            ctx.fillText(label, x, height - padding.bottom + 10);
        }
    });
    ctx.stroke();
    
    // Draw Points
    dataList.forEach((d, i) => {
        const x = padding.left + i * stepX;
        const y = height - padding.bottom - (d.count / maxVal) * (height - padding.top - padding.bottom);
        
        ctx.beginPath();
        ctx.arc(x, y, 4, 0, Math.PI * 2);
        ctx.fillStyle = '#0a0a0f';
        ctx.fill();
        ctx.lineWidth = 2;
        ctx.strokeStyle = '#4361ee';
        ctx.stroke();
    });
}

function drawPieChart(canvasId, dataMap) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;
    
    const container = canvas.parentElement;
    canvas.width = container.clientWidth;
    canvas.height = container.clientHeight;
    
    const ctx = canvas.getContext('2d');
    const width = canvas.width;
    const height = canvas.height;
    
    ctx.clearRect(0, 0, width, height);
    
    const entries = Object.entries(dataMap);
    if (entries.length === 0) {
        ctx.fillStyle = '#8892b0';
        ctx.font = '12px Inter, sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText('No Data', width/2, height/2);
        return;
    }
    
    // Sort by count desc
    entries.sort((a, b) => b[1] - a[1]);
    
    const total = entries.reduce((sum, [, count]) => sum + count, 0);
    const colors = ['#4361ee', '#7209b7', '#f72585', '#4cc9f0', '#f8961e', '#2a9d8f', '#e9c46a'];
    
    const centerX = width / 3;
    const centerY = height / 2;
    const radius = Math.min(centerX, centerY) * 0.8;
    
    let startAngle = -Math.PI / 2;
    
    entries.forEach((entry, i) => {
        const [label, count] = entry;
        const sliceAngle = (count / total) * 2 * Math.PI;
        const color = colors[i % colors.length];
        
        ctx.beginPath();
        ctx.moveTo(centerX, centerY);
        ctx.arc(centerX, centerY, radius, startAngle, startAngle + sliceAngle);
        ctx.closePath();
        
        ctx.fillStyle = color;
        ctx.fill();
        
        // Inner stroke for gap effect
        ctx.lineWidth = 2;
        ctx.strokeStyle = '#12121a';
        ctx.stroke();
        
        startAngle += sliceAngle;
        
        // Draw Legend
        const legendX = centerX + radius + 20;
        const legendY = centerY - radius + (i * 20) + 10;
        
        if (legendY < height) {
            ctx.beginPath();
            ctx.arc(legendX, legendY, 5, 0, Math.PI * 2);
            ctx.fillStyle = color;
            ctx.fill();
            
            ctx.fillStyle = '#ffffff';
            ctx.font = '12px Inter, sans-serif';
            ctx.textAlign = 'left';
            ctx.textBaseline = 'middle';
            
            const percentage = Math.round((count / total) * 100);
            const text = `${label} (${percentage}%)`;
            
            // Truncate text if needed
            ctx.fillText(text.substring(0, 15) + (text.length > 15 ? '...' : ''), legendX + 15, legendY);
        }
    });
    
    // Donut hole
    ctx.beginPath();
    ctx.arc(centerX, centerY, radius * 0.6, 0, Math.PI * 2);
    ctx.fillStyle = 'rgba(0,0,0,0.2)'; // Use section background
    ctx.fill();
}
