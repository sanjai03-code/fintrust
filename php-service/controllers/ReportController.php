<?php
declare(strict_types=1);

namespace FinTrust\Controllers;

use FinTrust\Config\Database;
use PDO;

class ReportController {
    public function getSummaryStats(): void {
        $pdo = Database::getConnection();

        // 1. Transaction Metrics
        $txStats = $pdo->query("
            SELECT 
                COUNT(*) AS total_count,
                COALESCE(SUM(amount), 0) AS total_volume,
                SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_count,
                SUM(CASE WHEN status = 'FLAGGED' OR status = 'UNDER_REVIEW' THEN 1 ELSE 0 END) AS flagged_count,
                SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_count,
                SUM(CASE WHEN risk_level = 'HIGH' OR risk_level = 'CRITICAL' THEN 1 ELSE 0 END) AS high_risk_count
            FROM transactions
        ")->fetch();

        // 2. Alert Metrics
        $alertStats = $pdo->query("
            SELECT 
                COUNT(*) AS total_alerts,
                SUM(CASE WHEN status = 'NEW' THEN 1 ELSE 0 END) AS new_alerts,
                SUM(CASE WHEN status = 'IN_REVIEW' THEN 1 ELSE 0 END) AS in_review_alerts,
                SUM(CASE WHEN status = 'RESOLVED' THEN 1 ELSE 0 END) AS resolved_alerts,
                SUM(CASE WHEN severity = 'CRITICAL' THEN 1 ELSE 0 END) AS critical_alerts,
                SUM(CASE WHEN severity = 'HIGH' THEN 1 ELSE 0 END) AS high_alerts
            FROM alerts
        ")->fetch();

        // 3. Ledger Metrics
        $ledgerStats = $pdo->query("
            SELECT 
                COUNT(*) AS total_entries,
                SUM(CASE WHEN is_verified = 1 OR is_verified = TRUE THEN 1 ELSE 0 END) AS verified_entries,
                SUM(CASE WHEN is_verified = 0 OR is_verified = FALSE THEN 1 ELSE 0 END) AS unverified_entries,
                COALESCE(SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END), 0) AS total_debit,
                COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END), 0) AS total_credit
            FROM ledger_entries
        ")->fetch();

        // 4. Organization Count
        $orgCount = $pdo->query("SELECT COUNT(*) AS org_count FROM organizations WHERE status = 'ACTIVE'")->fetch()['org_count'] ?? 0;

        // 5. Daily Transaction Trend (for Chart.js)
        $trendStmt = $pdo->query("
            SELECT 
                DATE(created_at) AS tx_date,
                COUNT(*) AS tx_count,
                COALESCE(SUM(amount), 0) AS daily_volume
            FROM transactions
            GROUP BY DATE(created_at)
            ORDER BY tx_date ASC
            LIMIT 14
        ");
        $trends = $trendStmt->fetchAll();

        // 6. Risk Tier Distribution
        $riskStmt = $pdo->query("
            SELECT risk_tier, COUNT(*) AS count
            FROM risk_scores
            GROUP BY risk_tier
        ");
        $riskDist = $riskStmt->fetchAll();

        $this->json([
            'success' => true,
            'metrics' => [
                'total_volume' => (float)$txStats['total_volume'],
                'total_transactions' => (int)$txStats['total_count'],
                'completed_transactions' => (int)$txStats['completed_count'],
                'flagged_transactions' => (int)$txStats['flagged_count'],
                'rejected_transactions' => (int)$txStats['rejected_count'],
                'high_risk_transactions' => (int)$txStats['high_risk_count'],
                'active_organizations' => (int)$orgCount,
                'alerts' => [
                    'total' => (int)$alertStats['total_alerts'],
                    'new' => (int)$alertStats['new_alerts'],
                    'in_review' => (int)$alertStats['in_review_alerts'],
                    'resolved' => (int)$alertStats['resolved_alerts'],
                    'critical' => (int)$alertStats['critical_alerts'],
                    'high' => (int)$alertStats['high_alerts']
                ],
                'ledger' => [
                    'total_entries' => (int)$ledgerStats['total_entries'],
                    'verified_entries' => (int)$ledgerStats['verified_entries'],
                    'unverified_entries' => (int)$ledgerStats['unverified_entries'],
                    'total_debit' => (float)$ledgerStats['total_debit'],
                    'total_credit' => (float)$ledgerStats['total_credit'],
                    'ledger_variance' => round((float)$ledgerStats['total_debit'] - (float)$ledgerStats['total_credit'], 2)
                ]
            ],
            'chart_data' => [
                'trends' => $trends,
                'risk_distribution' => $riskDist
            ]
        ]);
    }

    public function getComplianceReport(): void {
        $pdo = Database::getConnection();

        // Pull top risk items and open alerts
        $alerts = $pdo->query("
            SELECT a.*, o.name AS organization_name, t.transaction_ref, t.amount, t.currency
            FROM alerts a
            JOIN organizations o ON a.organization_id = o.id
            LEFT JOIN transactions t ON a.transaction_id = t.id
            WHERE a.status IN ('NEW', 'IN_REVIEW')
            ORDER BY CASE a.severity WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END ASC
        ")->fetchAll();

        $highRiskTx = $pdo->query("
            SELECT t.*, o.name AS organization_name, r.total_score, r.rule_matches
            FROM transactions t
            JOIN organizations o ON t.organization_id = o.id
            JOIN risk_scores r ON t.id = r.transaction_id
            WHERE r.total_score >= 70.0
            ORDER BY r.total_score DESC
        ")->fetchAll();

        $this->json([
            'success' => true,
            'report_generated_at' => date('c'),
            'regulatory_standard' => 'FinCEN / FATF / Basel III Compliant Framework',
            'unresolved_alerts' => $alerts,
            'flagged_transactions' => $highRiskTx
        ]);
    }

    private function json(array $data, int $status = 200): void {
        http_response_code($status);
        header('Content-Type: application/json');
        echo json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
        exit;
    }
}
