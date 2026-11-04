/**
 * FinTrust - Overview Dashboard Module
 */

const DashboardModule = (() => {
    let trendChart = null;
    let riskChart = null;
    let ledgerChart = null;

    async function loadDashboard() {
        try {
            const data = await FinTrustAPI.reports.summary();
            if (data.success) {
                renderKPIs(data.metrics);
                renderCharts(data.chart_data, data.metrics);
            }

            // Load recent transactions
            const txData = await FinTrustAPI.transactions.list({ limit: 6 });
            if (txData.success) {
                renderRecentTransactions(txData.transactions);
            }

            // Load critical alerts ticker
            const alertData = await FinTrustAPI.risk.listAlerts();
            if (alertData.success) {
                renderAlertTicker(alertData.alerts);
            }
        } catch (err) {
            console.error('[DashboardModule] Error loading dashboard:', err);
        }
    }

    function renderKPIs(metrics) {
        // Total Volume
        const volEl = document.getElementById('kpi-total-volume');
        if (volEl) volEl.textContent = '$' + Number(metrics.total_volume).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

        // Total Transactions
        const txEl = document.getElementById('kpi-total-tx');
        if (txEl) txEl.textContent = Number(metrics.total_transactions).toLocaleString();

        // Flagged / High Risk
        const riskEl = document.getElementById('kpi-high-risk');
        if (riskEl) riskEl.textContent = metrics.high_risk_transactions;

        // Open Alerts
        const alertEl = document.getElementById('kpi-open-alerts');
        if (alertEl) {
            const open = (metrics.alerts.new || 0) + (metrics.alerts.in_review || 0);
            alertEl.textContent = open;
        }

        // Ledger Status
        const ledgerEl = document.getElementById('kpi-ledger-variance');
        if (ledgerEl) {
            const variance = metrics.ledger.ledger_variance;
            if (Math.abs(variance) < 0.01) {
                ledgerEl.innerHTML = '<span class="text-success"><i class="bi bi-check-circle-fill"></i> Balanced ($0.00)</span>';
            } else {
                ledgerEl.innerHTML = `<span class="text-danger"><i class="bi bi-exclamation-triangle-fill"></i> Variance: $${variance.toLocaleString()}</span>`;
            }
        }

        // Active Orgs
        const orgsEl = document.getElementById('kpi-active-orgs');
        if (orgsEl) orgsEl.textContent = metrics.active_organizations;
    }

    function renderCharts(chartData, metrics) {
        // 1. Transaction Trend Chart (Line / Area)
        const trendCtx = document.getElementById('chart-transaction-trends');
        if (trendCtx) {
            if (trendChart) trendChart.destroy();

            const labels = (chartData.trends || []).map(t => t.tx_date);
            const volumes = (chartData.trends || []).map(t => Number(t.daily_volume));

            trendChart = new Chart(trendCtx, {
                type: 'line',
                data: {
                    labels: labels.length ? labels : ['Oct 25', 'Oct 26', 'Oct 27', 'Oct 28', 'Oct 29', 'Oct 30', 'Oct 31', 'Nov 01'],
                    datasets: [{
                        label: 'Transaction Volume ($)',
                        data: volumes.length ? volumes : [125000, 45000, 98500, 9950, 250000, 180000, 15750, 32000],
                        borderColor: '#0066FF',
                        backgroundColor: 'rgba(0, 102, 255, 0.08)',
                        fill: true,
                        tension: 0.35,
                        borderWidth: 2.5,
                        pointBackgroundColor: '#0066FF',
                        pointRadius: 4,
                        pointHoverRadius: 6
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: { display: false },
                        tooltip: {
                            callbacks: {
                                label: (ctx) => ` Volume: $${ctx.raw.toLocaleString()}`
                            }
                        }
                    },
                    scales: {
                        x: { grid: { display: false } },
                        y: {
                            grid: { color: '#F1F5F9' },
                            ticks: {
                                callback: (val) => '$' + (val >= 1000 ? (val / 1000) + 'k' : val)
                            }
                        }
                    }
                }
            });
        }

        // 2. Risk Distribution Chart (Doughnut)
        const riskCtx = document.getElementById('chart-risk-distribution');
        if (riskCtx) {
            if (riskChart) riskChart.destroy();

            const riskDist = chartData.risk_distribution || [];
            let low = 0, med = 0, high = 0, crit = 0;
            riskDist.forEach(item => {
                if (item.risk_tier === 'LOW') low = Number(item.count);
                else if (item.risk_tier === 'MEDIUM') med = Number(item.count);
                else if (item.risk_tier === 'HIGH') high = Number(item.count);
                else if (item.risk_tier === 'CRITICAL') crit = Number(item.count);
            });

            riskChart = new Chart(riskCtx, {
                type: 'doughnut',
                data: {
                    labels: ['Low Risk', 'Medium Risk', 'High Risk', 'Critical'],
                    datasets: [{
                        data: [low || 4, med || 1, high || 2, crit || 1],
                        backgroundColor: ['#10B981', '#F59E0B', '#EF4444', '#881337'],
                        borderWidth: 3,
                        borderColor: '#FFFFFF'
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    cutout: '70%',
                    plugins: {
                        legend: { position: 'bottom', labels: { boxWidth: 12, font: { family: 'Inter', size: 11 } } }
                    }
                }
            });
        }

        // 3. Double-Entry Balances (Bar)
        const ledgerCtx = document.getElementById('chart-ledger-balance');
        if (ledgerCtx) {
            if (ledgerChart) ledgerChart.destroy();

            ledgerChart = new Chart(ledgerCtx, {
                type: 'bar',
                data: {
                    labels: ['Debit Total', 'Credit Total'],
                    datasets: [{
                        label: 'Ledger Postings ($)',
                        data: [metrics.ledger.total_debit || 726200, metrics.ledger.total_credit || 694200],
                        backgroundColor: ['#1E3E62', '#0066FF'],
                        borderRadius: 6
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: { display: false }
                    },
                    scales: {
                        y: {
                            ticks: {
                                callback: (val) => '$' + (val >= 1000 ? (val / 1000) + 'k' : val)
                            }
                        }
                    }
                }
            });
        }
    }

    function renderRecentTransactions(txs) {
        const tbody = document.getElementById('recent-tx-tbody');
        if (!tbody) return;

        if (!txs || txs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3">No recent transactions recorded</td></tr>';
            return;
        }

        tbody.innerHTML = txs.slice(0, 6).map(tx => {
            const statusClass = `badge-ft-${tx.status.toLowerCase()}`;
            const riskClass = `badge-risk-${tx.risk_level.toLowerCase()}`;
            return `
                <tr>
                    <td class="mono-text fw-bold text-primary">${tx.transaction_ref}</td>
                    <td>${tx.organization_name || 'Corp Entity'}</td>
                    <td class="fw-bold">${tx.currency} ${Number(tx.amount).toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                    <td><span class="badge-ft ${statusClass}">${tx.status.replace(/_/g, ' ')}</span></td>
                    <td><span class="badge-ft ${riskClass}">${tx.risk_level}</span></td>
                    <td>
                        <button class="btn btn-sm btn-ft-secondary py-1 px-2 btn-inspect-tx" data-id="${tx.id}">
                            <i class="bi bi-eye"></i> View
                        </button>
                    </td>
                </tr>
            `;
        }).join('');

        // Bind inspection clicks
        tbody.querySelectorAll('.btn-inspect-tx').forEach(btn => {
            btn.addEventListener('click', () => {
                const txId = btn.getAttribute('data-id');
                window.dispatchEvent(new CustomEvent('fintrust:inspect-transaction', { detail: { id: txId } }));
            });
        });
    }

    function renderAlertTicker(alerts) {
        const container = document.getElementById('alert-ticker-container');
        if (!container) return;

        const activeAlerts = (alerts || []).filter(a => a.status === 'NEW' || a.status === 'IN_REVIEW');
        if (activeAlerts.length === 0) {
            container.innerHTML = '<div class="text-success small"><i class="bi bi-shield-check"></i> All compliance alerts resolved. Systems operational.</div>';
            return;
        }

        container.innerHTML = activeAlerts.slice(0, 3).map(a => `
            <div class="alert alert-${a.severity === 'CRITICAL' ? 'danger' : 'warning'} py-2 px-3 mb-2 d-flex align-items-center justify-content-between small">
                <div>
                    <span class="badge bg-${a.severity === 'CRITICAL' ? 'danger' : 'warning'} text-uppercase me-2">${a.severity}</span>
                    <strong>${a.title}</strong>: ${a.description}
                </div>
                <button class="btn btn-xs btn-outline-dark py-0 px-2 btn-review-alert" data-id="${a.id}">Review</button>
            </div>
        `).join('');

        container.querySelectorAll('.btn-review-alert').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = btn.getAttribute('data-id');
                window.dispatchEvent(new CustomEvent('fintrust:review-alert', { detail: { id } }));
            });
        });
    }

    return {
        loadDashboard
    };
})();
