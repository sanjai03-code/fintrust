<?php
declare(strict_types=1);

namespace FinTrust\Controllers;

use FinTrust\Config\Database;
use PDO;

class OrgController {
    public function listOrgs(): void {
        $pdo = Database::getConnection();
        $sql = "
            SELECT 
                o.*,
                (SELECT COUNT(*) FROM employees e WHERE e.organization_id = o.id) AS employee_count,
                (SELECT COUNT(*) FROM transactions t WHERE t.organization_id = o.id) AS transaction_count,
                (SELECT COALESCE(SUM(t.amount), 0) FROM transactions t WHERE t.organization_id = o.id) AS total_volume
            FROM organizations o
            ORDER BY o.id ASC
        ";
        $stmt = $pdo->query($sql);
        $orgs = $stmt->fetchAll();

        $this->json(['success' => true, 'organizations' => $orgs]);
    }

    public function getOrg(int $id): void {
        $pdo = Database::getConnection();
        $stmt = $pdo->prepare("SELECT * FROM organizations WHERE id = ?");
        $stmt->execute([$id]);
        $org = $stmt->fetch();

        if (!$org) {
            $this->json(['success' => false, 'error' => 'Organization not found'], 404);
            return;
        }

        $this->json(['success' => true, 'organization' => $org]);
    }

    public function createOrg(array $currentUser): void {
        $input = json_decode(file_get_contents('php://input'), true) ?? [];
        $name = trim($input['name'] ?? '');
        $regNum = trim($input['registration_number'] ?? '');
        $taxId = trim($input['tax_id'] ?? '');
        $country = strtoupper(trim($input['country'] ?? 'USA'));
        $currency = strtoupper(trim($input['currency'] ?? 'USD'));
        $riskTier = strtoupper(trim($input['risk_tier'] ?? 'LOW'));
        $status = strtoupper(trim($input['status'] ?? 'ACTIVE'));
        $complianceContact = trim($input['compliance_contact'] ?? '');
        $turnover = (float)($input['annual_turnover'] ?? 0.0);

        if (empty($name) || empty($regNum) || empty($taxId)) {
            $this->json(['success' => false, 'error' => 'Name, registration number, and tax ID are required'], 400);
            return;
        }

        $pdo = Database::getConnection();

        // Check uniqueness
        $check = $pdo->prepare("SELECT id FROM organizations WHERE registration_number = ? OR tax_id = ?");
        $check->execute([$regNum, $taxId]);
        if ($check->fetch()) {
            $this->json(['success' => false, 'error' => 'An organization with this registration number or tax ID already exists'], 409);
            return;
        }

        $stmt = $pdo->prepare("
            INSERT INTO organizations (name, registration_number, tax_id, country, currency, risk_tier, status, compliance_contact, annual_turnover)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ");
        $stmt->execute([$name, $regNum, $taxId, $country, $currency, $riskTier, $status, $complianceContact, $turnover]);
        $newId = (int)$pdo->lastInsertId();

        $this->recordAudit($currentUser, 'ORGANIZATION_CREATED', 'ORGANIZATION', (string)$newId, $newId, null, [
            'name' => $name,
            'reg_num' => $regNum,
            'risk_tier' => $riskTier
        ]);

        $this->json([
            'success' => true,
            'message' => 'Organization created successfully',
            'organization_id' => $newId
        ], 201);
    }

    public function updateOrg(int $id, array $currentUser): void {
        $input = json_decode(file_get_contents('php://input'), true) ?? [];
        $pdo = Database::getConnection();

        $stmt = $pdo->prepare("SELECT * FROM organizations WHERE id = ?");
        $stmt->execute([$id]);
        $old = $stmt->fetch();
        if (!$old) {
            $this->json(['success' => false, 'error' => 'Organization not found'], 404);
            return;
        }

        $name = trim($input['name'] ?? $old['name']);
        $country = strtoupper(trim($input['country'] ?? $old['country']));
        $currency = strtoupper(trim($input['currency'] ?? $old['currency']));
        $riskTier = strtoupper(trim($input['risk_tier'] ?? $old['risk_tier']));
        $status = strtoupper(trim($input['status'] ?? $old['status']));
        $complianceContact = trim($input['compliance_contact'] ?? $old['compliance_contact']);
        $turnover = isset($input['annual_turnover']) ? (float)$input['annual_turnover'] : (float)$old['annual_turnover'];

        $update = $pdo->prepare("
            UPDATE organizations 
            SET name = ?, country = ?, currency = ?, risk_tier = ?, status = ?, compliance_contact = ?, annual_turnover = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
        ");
        $update->execute([$name, $country, $currency, $riskTier, $status, $complianceContact, $turnover, $id]);

        $this->recordAudit($currentUser, 'ORGANIZATION_UPDATED', 'ORGANIZATION', (string)$id, $id, $old, [
            'name' => $name,
            'risk_tier' => $riskTier,
            'status' => $status
        ]);

        $this->json(['success' => true, 'message' => 'Organization updated successfully']);
    }

    public function listEmployees(int $orgId): void {
        $pdo = Database::getConnection();
        $stmt = $pdo->prepare("
            SELECT 
                e.id AS employee_id,
                e.organization_id,
                e.department,
                e.designation,
                e.role AS org_role,
                e.status AS employee_status,
                e.joined_date,
                u.id AS user_id,
                u.full_name,
                u.email,
                u.phone
            FROM employees e
            JOIN users u ON e.user_id = u.id
            WHERE e.organization_id = ?
            ORDER BY e.id ASC
        ");
        $stmt->execute([$orgId]);
        $employees = $stmt->fetchAll();

        $this->json(['success' => true, 'employees' => $employees]);
    }

    public function addEmployee(int $orgId, array $currentUser): void {
        $input = json_decode(file_get_contents('php://input'), true) ?? [];
        $userId = (int)($input['user_id'] ?? 0);
        $department = trim($input['department'] ?? 'Finance');
        $designation = trim($input['designation'] ?? 'Finance Officer');
        $role = strtoupper(trim($input['role'] ?? 'ANALYST'));
        $joinedDate = trim($input['joined_date'] ?? date('Y-m-d'));

        if ($userId <= 0) {
            $this->json(['success' => false, 'error' => 'Valid user_id is required'], 400);
            return;
        }

        $pdo = Database::getConnection();

        // Check if already assigned
        $check = $pdo->prepare("SELECT id FROM employees WHERE organization_id = ? AND user_id = ?");
        $check->execute([$orgId, $userId]);
        if ($check->fetch()) {
            $this->json(['success' => false, 'error' => 'User is already assigned to this organization'], 409);
            return;
        }

        $stmt = $pdo->prepare("
            INSERT INTO employees (organization_id, user_id, department, designation, role, status, joined_date)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?)
        ");
        $stmt->execute([$orgId, $userId, $department, $designation, $role, $joinedDate]);
        $newEmployeeId = (int)$pdo->lastInsertId();

        $this->recordAudit($currentUser, 'EMPLOYEE_ASSIGNED', 'EMPLOYEE', (string)$newEmployeeId, $orgId, null, [
            'user_id' => $userId,
            'department' => $department,
            'role' => $role
        ]);

        $this->json([
            'success' => true,
            'message' => 'Employee assigned to organization successfully',
            'employee_id' => $newEmployeeId
        ], 201);
    }

    private function recordAudit(array $user, string $action, string $entityType, string $entityId, ?int $orgId, ?array $old, ?array $new): void {
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
            // Silently continue
        }
    }

    private function json(array $data, int $status = 200): void {
        http_response_code($status);
        header('Content-Type: application/json');
        echo json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
        exit;
    }
}
