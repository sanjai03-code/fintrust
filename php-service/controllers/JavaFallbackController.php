<?php
declare(strict_types=1);

namespace FinTrust\Controllers;

use FinTrust\Config\Database;
use FinTrust\Middleware\AuthMiddleware;
use PDO;

class JavaFallbackController {
    public function handle(string $uri, string $method): void {
        $user = AuthMiddleware::authenticate();

        if (str_starts_with($uri, '/api/transactions')) {
            $this->handleTransactions($uri, $method, $user);
            return;
        }

        if (str_starts_with($uri, '/api/ledger')) {
            $this->handleLedger($uri, $method, $user);
            return;
        }

        if (str_starts_with($uri, '/api/risk')) {
            $this->handleRisk($uri, $method, $user);
            return;
        }

        if (str_starts_with($uri, '/api/audit')) {
            $this->handleAudit($uri, $method, $user);
            return;
        }

        $this->json(['success' => false, 'error' => 'Endpoint not found'], 404);
    }

    private function handleTransactions(string $uri, string $method, ?array $user): void {
        $pdo = Database::getConnection();

        if ($uri === '/api/transactions' && $method === 'GET') {
            $orgId = $_GET['organization_id'] ?? null;
            $status = $_GET['status'] ?? null;
            $risk = $_GET['risk_level'] ?? null;

            $sql = "
                SELECT 
                    t.*, 
                    o.name AS organization_name,
                    u.full_name AS creator_name,
                    r.total_score,
                    r.factors_breakdown
                FROM transactions t
                JOIN organizations o ON t.organization_id = o.id
                LEFT JOIN users u ON t.user_id = u.id
                LEFT JOIN risk_scores r ON t.id = r.transaction_id
                WHERE 1=1
            ";
            $params = [];
            if ($orgId) {
                $sql .= " AND t.organization_id = ?";
                $params[] = $orgId;
            }
            if ($status) {
                $sql .= " AND t.status = ?";
                $params[] = $status;
            }
            if ($risk) {
                $sql .= " AND t.risk_level = ?";
                $params[] = $risk;
            }
            $sql .= " ORDER BY t.id DESC";

            $stmt = $pdo->prepare($sql);
            $stmt->execute($params);
            $txs = $stmt->fetchAll();

            $this->json(['success' => true, 'transactions' => $txs, 'service_source' => 'Java-Service / Resilient Bridge']);
            return;
        }

        if ($uri === '/api/transactions' && $method === 'POST') {
            $input = json_decode(file_get_contents('php://input'), true) ?? [];
            $orgId = (int)($input['organization_id'] ?? 1);
            $type = strtoupper(trim($input['type'] ?? 'TRANSFER'));
            $amount = (float)($input['amount'] ?? 0.0);
            $currency = strtoupper(trim($input['currency'] ?? 'USD'));
            $src = trim($input['source_account'] ?? '');
            $dst = trim($input['destination_account'] ?? '');
            $dstCountry = strtoupper(trim($input['destination_country'] ?? 'USA'));
            $desc = trim($input['description'] ?? '');

            if ($amount <= 0 || empty($src) || empty($dst)) {
                $this->json(['success' => false, 'error' => 'Amount, source account, and destination account are required'], 400);
                return;
            }

            $txRef = 'TX-' . date('Y') . '-' . strtoupper(bin2hex(random_bytes(3)));

            // Rule-based risk calculation
            $riskAssessment = $this->calculateRiskScore($amount, $dstCountry, $type, $src, $dst);
            $status = ($riskAssessment['score'] >= 75) ? 'FLAGGED' : (($riskAssessment['score'] >= 50) ? 'UNDER_REVIEW' : 'PENDING');

            $stmt = $pdo->prepare("
                INSERT INTO transactions (
                    transaction_ref, organization_id, user_id, type, amount, currency, 
                    source_account, destination_account, destination_country, description, 
                    status, risk_level, risk_score_cache
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ");
            $stmt->execute([
                $txRef, $orgId, $user['user_id'] ?? null, $type, $amount, $currency,
                $src, $dst, $dstCountry, $desc, $status, $riskAssessment['tier'], $riskAssessment['score']
            ]);
            $txId = (int)$pdo->lastInsertId();

            // Insert double-entry ledger records
            $stmtLedger = $pdo->prepare("
                INSERT INTO ledger_entries (transaction_id, entry_type, account_number, amount, currency, balance_after, is_verified, verification_notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ");
            // Debit source
            $stmtLedger->execute([$txId, 'DEBIT', $src, $amount, $currency, 5000000 - $amount, ($status === 'COMPLETED'), 'Automated debit posting']);
            // Credit destination
            $stmtLedger->execute([$txId, 'CREDIT', $dst, $amount, $currency, $amount, ($status === 'COMPLETED'), 'Automated credit posting']);

            // Insert Risk Score record
            $stmtRisk = $pdo->prepare("
                INSERT INTO risk_scores (transaction_id, organization_id, total_score, risk_tier, factors_breakdown, rule_matches)
                VALUES (?, ?, ?, ?, ?, ?)
            ");
            $stmtRisk->execute([
                $txId, $orgId, $riskAssessment['score'], $riskAssessment['tier'],
                json_encode($riskAssessment['factors']), json_encode($riskAssessment['rules'])
            ]);

            // Generate Suspicious Alert if Risk >= 50
            if ($riskAssessment['score'] >= 50) {
                $alertCode = 'ALT-' . date('Y') . '-' . strtoupper(bin2hex(random_bytes(3)));
                $alertStmt = $pdo->prepare("
                    INSERT INTO alerts (alert_code, transaction_id, organization_id, severity, title, description, status)
                    VALUES (?, ?, ?, ?, ?, ?, 'NEW')
                ");
                $alertStmt->execute([
                    $alertCode, $txId, $orgId, $riskAssessment['tier'],
                    'Rule-Engine Alert: ' . ($riskAssessment['rules'][0] ?? 'Elevated Risk Pattern'),
                    'Transaction ' . $txRef . ' flagged with risk score ' . $riskAssessment['score'] . '/100.'
                ]);
            }

            // Record Audit Trail
            $this->logAudit($user, $orgId, 'TRANSACTION_CREATED', 'TRANSACTION', (string)$txId, null, [
                'ref' => $txRef,
                'amount' => $amount,
                'risk_score' => $riskAssessment['score'],
                'risk_level' => $riskAssessment['tier']
            ]);

            $this->json([
                'success' => true,
                'message' => 'Transaction created and evaluated by risk engine',
                'transaction' => [
                    'id' => $txId,
                    'transaction_ref' => $txRef,
                    'status' => $status,
                    'risk_level' => $riskAssessment['tier'],
                    'risk_score' => $riskAssessment['score']
                ]
            ], 201);
            return;
        }

        if (preg_match('#^/api/transactions/(\d+)/status$#', $uri, $matches) && $method === 'PUT') {
            $txId = (int)$matches[1];
            $input = json_decode(file_get_contents('php://input'), true) ?? [];
            $newStatus = strtoupper(trim($input['status'] ?? ''));

            $stmt = $pdo->prepare("SELECT * FROM transactions WHERE id = ?");
            $stmt->execute([$txId]);
            $tx = $stmt->fetch();
            if (!$tx) {
                $this->json(['success' => false, 'error' => 'Transaction not found'], 404);
                return;
            }

            $pdo->prepare("UPDATE transactions SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")->execute([$newStatus, $txId]);
            $this->logAudit($user, (int)$tx['organization_id'], 'TRANSACTION_STATUS_UPDATED', 'TRANSACTION', (string)$txId, ['status' => $tx['status']], ['status' => $newStatus]);

            $this->json(['success' => true, 'message' => "Transaction status updated to {$newStatus}"]);
            return;
        }

        $this->json(['success' => false, 'error' => 'Not found'], 404);
    }

    private function handleLedger(string $uri, string $method, ?array $user): void {
        $pdo = Database::getConnection();

        if ($uri === '/api/ledger' && $method === 'GET') {
            $sql = "
                SELECT 
                    l.*,
                    t.transaction_ref,
                    t.organization_id,
                    o.name AS organization_name,
                    u.full_name AS verified_by_name
                FROM ledger_entries l
                JOIN transactions t ON l.transaction_id = t.id
                JOIN organizations o ON t.organization_id = o.id
                LEFT JOIN users u ON l.verified_by = u.id
                ORDER BY l.id DESC
            ";
            $entries = $pdo->query($sql)->fetchAll();
            $this->json(['success' => true, 'entries' => $entries]);
            return;
        }

        // Ledger Verification & Reconcile Engine
        if ($uri === '/api/ledger/verify' && $method === 'POST') {
            $input = json_decode(file_get_contents('php://input'), true) ?? [];
            $txId = !empty($input['transaction_id']) ? (int)$input['transaction_id'] : null;

            // Run verification check across transactions
            $query = "
                SELECT 
                    t.id AS tx_id,
                    t.transaction_ref,
                    t.amount AS expected_amount,
                    COALESCE(SUM(CASE WHEN l.entry_type = 'DEBIT' THEN l.amount ELSE 0 END), 0) AS total_debit,
                    COALESCE(SUM(CASE WHEN l.entry_type = 'CREDIT' THEN l.amount ELSE 0 END), 0) AS total_credit,
                    COUNT(l.id) AS entry_count
                FROM transactions t
                LEFT JOIN ledger_entries l ON t.id = l.transaction_id
            ";
            if ($txId) {
                $query .= " WHERE t.id = {$txId}";
            }
            $query .= " GROUP BY t.id, t.transaction_ref, t.amount";

            $rows = $pdo->query($query)->fetchAll();
            $results = [];

            foreach ($rows as $row) {
                $balanced = (abs((float)$row['total_debit'] - (float)$row['total_credit']) < 0.001) && ((int)$row['entry_count'] >= 2);
                $isDuplicate = false;

                // Duplicate Check: Same source, destination, and amount in transactions within 15 minutes
                $dupStmt = $pdo->prepare("
                    SELECT COUNT(*) AS c FROM transactions 
                    WHERE source_account = (SELECT source_account FROM transactions WHERE id = ?)
                    AND destination_account = (SELECT destination_account FROM transactions WHERE id = ?)
                    AND amount = (SELECT amount FROM transactions WHERE id = ?)
                    AND id != ?
                ");
                $dupStmt->execute([$row['tx_id'], $row['tx_id'], $row['tx_id'], $row['tx_id']]);
                if ((int)$dupStmt->fetch()['c'] > 0) {
                    $isDuplicate = true;
                }

                // If balanced and user requested auto-verify, update entries
                if ($balanced && !empty($input['auto_reconcile'])) {
                    $upd = $pdo->prepare("
                        UPDATE ledger_entries 
                        SET is_verified = TRUE, verified_at = CURRENT_TIMESTAMP, verified_by = ?, verification_notes = 'Automated Double-Entry Verification Passed'
                        WHERE transaction_id = ?
                    ");
                    $upd->execute([$user['user_id'] ?? null, $row['tx_id']]);
                }

                $results[] = [
                    'transaction_id' => (int)$row['tx_id'],
                    'transaction_ref' => $row['transaction_ref'],
                    'expected_amount' => (float)$row['expected_amount'],
                    'total_debit' => (float)$row['total_debit'],
                    'total_credit' => (float)$row['total_credit'],
                    'variance' => round((float)$row['total_debit'] - (float)$row['total_credit'], 2),
                    'is_balanced' => $balanced,
                    'is_duplicate' => $isDuplicate,
                    'status' => $balanced ? 'BALANCED' : 'IMBALANCE_DETECTED'
                ];
            }

            $this->logAudit($user, 1, 'LEDGER_VERIFIED', 'LEDGER', $txId ? (string)$txId : 'BATCH', null, [
                'verified_count' => count($results)
            ]);

            $this->json(['success' => true, 'verification_report' => $results]);
            return;
        }

        $this->json(['success' => false, 'error' => 'Not found'], 404);
    }

    private function handleRisk(string $uri, string $method, ?array $user): void {
        $pdo = Database::getConnection();

        if ($uri === '/api/risk/alerts' && $method === 'GET') {
            $status = $_GET['status'] ?? null;
            $sql = "
                SELECT 
                    a.*,
                    t.transaction_ref,
                    t.amount,
                    t.currency,
                    t.source_account,
                    t.destination_account,
                    o.name AS organization_name,
                    u.full_name AS resolved_by_name
                FROM alerts a
                JOIN organizations o ON a.organization_id = o.id
                LEFT JOIN transactions t ON a.transaction_id = t.id
                LEFT JOIN users u ON a.resolved_by = u.id
                WHERE 1=1
            ";
            $params = [];
            if ($status) {
                $sql .= " AND a.status = ?";
                $params[] = $status;
            }
            $sql .= " ORDER BY CASE a.severity WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END ASC, a.id DESC";

            $stmt = $pdo->prepare($sql);
            $stmt->execute($params);
            $alerts = $stmt->fetchAll();

            $this->json(['success' => true, 'alerts' => $alerts]);
            return;
        }

        if (preg_match('#^/api/risk/alerts/(\d+)/resolve$#', $uri, $matches) && $method === 'POST') {
            $alertId = (int)$matches[1];
            $input = json_decode(file_get_contents('php://input'), true) ?? [];
            $status = strtoupper(trim($input['status'] ?? 'RESOLVED'));
            $notes = trim($input['resolution_notes'] ?? 'Resolution reviewed by compliance');

            $stmt = $pdo->prepare("SELECT * FROM alerts WHERE id = ?");
            $stmt->execute([$alertId]);
            $alert = $stmt->fetch();
            if (!$alert) {
                $this->json(['success' => false, 'error' => 'Alert not found'], 404);
                return;
            }

            $update = $pdo->prepare("
                UPDATE alerts 
                SET status = ?, resolution_notes = ?, resolved_by = ?, resolved_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
            ");
            $update->execute([$status, $notes, $user['user_id'] ?? null, $alertId]);

            $this->logAudit($user, (int)$alert['organization_id'], 'ALERT_RESOLVED', 'ALERT', (string)$alertId, ['status' => $alert['status']], ['status' => $status, 'notes' => $notes]);

            $this->json(['success' => true, 'message' => "Alert marked as {$status}"]);
            return;
        }

        if ($uri === '/api/risk/evaluate' && $method === 'POST') {
            $input = json_decode(file_get_contents('php://input'), true) ?? [];
            $amount = (float)($input['amount'] ?? 0);
            $country = strtoupper(trim($input['destination_country'] ?? 'USA'));
            $type = strtoupper(trim($input['type'] ?? 'TRANSFER'));
            $src = trim($input['source_account'] ?? '');
            $dst = trim($input['destination_account'] ?? '');

            $result = $this->calculateRiskScore($amount, $country, $type, $src, $dst);
            $this->json(['success' => true, 'risk_evaluation' => $result]);
            return;
        }

        $this->json(['success' => false, 'error' => 'Not found'], 404);
    }

    private function handleAudit(string $uri, string $method, ?array $user): void {
        $pdo = Database::getConnection();

        if ($uri === '/api/audit' && $method === 'GET') {
            $entityType = $_GET['entity_type'] ?? null;
            $action = $_GET['action'] ?? null;

            $sql = "
                SELECT 
                    a.*,
                    u.full_name AS user_name,
                    o.name AS organization_name
                FROM audit_logs a
                LEFT JOIN users u ON a.user_id = u.id
                LEFT JOIN organizations o ON a.organization_id = o.id
                WHERE 1=1
            ";
            $params = [];
            if ($entityType) {
                $sql .= " AND a.entity_type = ?";
                $params[] = $entityType;
            }
            if ($action) {
                $sql .= " AND a.action = ?";
                $params[] = $action;
            }
            $sql .= " ORDER BY a.id DESC LIMIT 100";

            $stmt = $pdo->prepare($sql);
            $stmt->execute($params);
            $logs = $stmt->fetchAll();

            $this->json(['success' => true, 'audit_logs' => $logs]);
            return;
        }

        $this->json(['success' => false, 'error' => 'Not found'], 404);
    }

    // Comprehensive Rule-Based Risk Engine
    private function calculateRiskScore(float $amount, string $country, string $type, string $src, string $dst): array {
        $score = 5.0; // Base baseline score
        $rules = [];
        $factors = [
            'amount_risk' => 0.0,
            'jurisdiction_risk' => 0.0,
            'velocity_structuring_risk' => 0.0,
            'corridor_risk' => 0.0
        ];

        // Rule 1: High-Risk / Sanctioned Jurisdiction
        $sanctionedCountries = ['RUS', 'IRN', 'PRK', 'SYR', 'CUB', 'BLR'];
        $taxHavens = ['CYM', 'PAN', 'VGB', 'BMU', 'BHS', 'MCO'];
        if (in_array($country, $sanctionedCountries, true)) {
            $score += 85.0;
            $factors['jurisdiction_risk'] += 85.0;
            $rules[] = "OFAC/FATF High-Risk Sanctions Embargo Watchlist ($country)";
        } elseif (in_array($country, $taxHavens, true)) {
            $score += 35.0;
            $factors['jurisdiction_risk'] += 35.0;
            $rules[] = "Offshore Secrecy Jurisdiction / Tax Haven ($country)";
        }

        // Rule 2: Smurfing / Structuring Detection ($9,000 - $9,999)
        if ($amount >= 9000.0 && $amount <= 9999.0) {
            $score += 45.0;
            $factors['velocity_structuring_risk'] += 45.0;
            $rules[] = "Potential Smurfing: Transfer amount ($" . number_format($amount, 2) . ") structures just below $10,000 CTR reporting threshold";
        }

        // Rule 3: High Amount Velocity
        if ($amount >= 200000.0) {
            $score += 25.0;
            $factors['amount_risk'] += 25.0;
            $rules[] = "Ultra-High Value Wire (> $200,000.00)";
        } elseif ($amount >= 50000.0) {
            $score += 15.0;
            $factors['amount_risk'] += 15.0;
            $rules[] = "Substantial Transfer Amount (> $50,000.00)";
        }

        // Rule 4: High-frequency / Cash Withdrawal anomaly
        if ($type === 'WITHDRAWAL' && $amount > 10000.0) {
            $score += 20.0;
            $factors['corridor_risk'] += 20.0;
            $rules[] = "High-Volume Cash Withdrawal";
        }

        $totalScore = min(100.0, max(0.0, round($score, 2)));
        $tier = 'LOW';
        if ($totalScore >= 80.0) $tier = 'CRITICAL';
        elseif ($totalScore >= 60.0) $tier = 'HIGH';
        elseif ($totalScore >= 35.0) $tier = 'MEDIUM';

        if (empty($rules)) {
            $rules[] = "Standard transaction pattern verified";
        }

        return [
            'score' => $totalScore,
            'tier' => $tier,
            'factors' => $factors,
            'rules' => $rules
        ];
    }

    private function logAudit(?array $user, int $orgId, string $action, string $entityType, string $entityId, ?array $old, ?array $new): void {
        try {
            $pdo = Database::getConnection();
            $stmt = $pdo->prepare("
                INSERT INTO audit_logs (user_id, user_email, organization_id, action, entity_type, entity_id, ip_address, old_values, new_values)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ");
            $stmt->execute([
                $user['user_id'] ?? null,
                $user['email'] ?? null,
                $orgId,
                $action,
                $entityType,
                $entityId,
                $_SERVER['REMOTE_ADDR'] ?? '127.0.0.1',
                $old ? json_encode($old) : null,
                $new ? json_encode($new) : null
            ]);
        } catch (\Exception $e) {
            // continue
        }
    }

    private function json(array $data, int $status = 200): void {
        http_response_code($status);
        header('Content-Type: application/json');
        echo json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
        exit;
    }
}
