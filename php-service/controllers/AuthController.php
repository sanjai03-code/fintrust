<?php
declare(strict_types=1);

namespace FinTrust\Controllers;

use FinTrust\Config\Database;
use FinTrust\Config\JwtHandler;
use PDO;

class AuthController {
    public function login(): void {
        $input = json_decode(file_get_contents('php://input'), true) ?? [];
        $email = trim($input['email'] ?? '');
        $password = trim($input['password'] ?? '');

        if (empty($email) || empty($password)) {
            $this->json(['success' => false, 'error' => 'Email and password are required'], 400);
            return;
        }

        $pdo = Database::getConnection();
        $stmt = $pdo->prepare("SELECT id, email, password_hash, full_name, role, status, department FROM users WHERE email = ?");
        $stmt->execute([$email]);
        $user = $stmt->fetch();

        if (!$user || !password_verify($password, $user['password_hash'])) {
            $this->json(['success' => false, 'error' => 'Invalid email or password'], 401);
            return;
        }

        if ($user['status'] !== 'ACTIVE') {
            $this->json(['success' => false, 'error' => 'User account is ' . $user['status']], 403);
            return;
        }

        // Update last login
        $pdo->prepare("UPDATE users SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?")->execute([$user['id']]);

        // Generate JWT
        $tokenPayload = [
            'sub' => (string)$user['id'],
            'user_id' => (int)$user['id'],
            'email' => $user['email'],
            'full_name' => $user['full_name'],
            'role' => $user['role'],
            'department' => $user['department']
        ];
        $token = JwtHandler::encode($tokenPayload);

        // Record Audit Log
        $this->recordAudit((int)$user['id'], $user['email'], 'USER_LOGIN', 'USER', (string)$user['id'], null, [
            'login_time' => date('c'),
            'ip' => $_SERVER['REMOTE_ADDR'] ?? '127.0.0.1'
        ]);

        unset($user['password_hash']);

        $this->json([
            'success' => true,
            'message' => 'Authentication successful',
            'token' => $token,
            'user' => $user
        ]);
    }

    public function register(): void {
        $input = json_decode(file_get_contents('php://input'), true) ?? [];
        $email = trim($input['email'] ?? '');
        $password = trim($input['password'] ?? '');
        $fullName = trim($input['full_name'] ?? '');
        $role = strtoupper(trim($input['role'] ?? 'ANALYST'));
        $department = trim($input['department'] ?? 'Risk Operations');
        $phone = trim($input['phone'] ?? '');

        if (empty($email) || empty($password) || empty($fullName)) {
            $this->json(['success' => false, 'error' => 'Email, password, and full name are required'], 400);
            return;
        }

        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
            $this->json(['success' => false, 'error' => 'Invalid email address format'], 400);
            return;
        }

        if (strlen($password) < 8) {
            $this->json(['success' => false, 'error' => 'Password must be at least 8 characters long'], 400);
            return;
        }

        $validRoles = ['ADMIN', 'COMPLIANCE_OFFICER', 'AUDITOR', 'MANAGER', 'ANALYST'];
        if (!in_array($role, $validRoles, true)) {
            $role = 'ANALYST';
        }

        $pdo = Database::getConnection();
        $checkStmt = $pdo->prepare("SELECT id FROM users WHERE email = ?");
        $checkStmt->execute([$email]);
        if ($checkStmt->fetch()) {
            $this->json(['success' => false, 'error' => 'A user with this email already exists'], 409);
            return;
        }

        $passwordHash = password_hash($password, PASSWORD_BCRYPT);
        $stmt = $pdo->prepare("
            INSERT INTO users (email, password_hash, full_name, role, status, phone, department)
            VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
        ");
        $stmt->execute([$email, $passwordHash, $fullName, $role, $phone, $department]);
        $newUserId = (int)$pdo->lastInsertId();

        $tokenPayload = [
            'sub' => (string)$newUserId,
            'user_id' => $newUserId,
            'email' => $email,
            'full_name' => $fullName,
            'role' => $role,
            'department' => $department
        ];
        $token = JwtHandler::encode($tokenPayload);

        $this->recordAudit($newUserId, $email, 'USER_REGISTERED', 'USER', (string)$newUserId, null, [
            'role' => $role,
            'department' => $department
        ]);

        $this->json([
            'success' => true,
            'message' => 'User registration successful',
            'token' => $token,
            'user' => [
                'id' => $newUserId,
                'email' => $email,
                'full_name' => $fullName,
                'role' => $role,
                'department' => $department
            ]
        ], 201);
    }

    public function me(array $currentUser): void {
        $pdo = Database::getConnection();
        $stmt = $pdo->prepare("SELECT id, email, full_name, role, status, phone, department, last_login_at, created_at FROM users WHERE id = ?");
        $stmt->execute([$currentUser['user_id']]);
        $user = $stmt->fetch();

        if (!$user) {
            $this->json(['success' => false, 'error' => 'User not found'], 404);
            return;
        }

        $this->json(['success' => true, 'user' => $user]);
    }

    public function listUsers(): void {
        $pdo = Database::getConnection();
        $stmt = $pdo->query("SELECT id, email, full_name, role, status, department, created_at FROM users ORDER BY id ASC");
        $users = $stmt->fetchAll();
        $this->json(['success' => true, 'users' => $users]);
    }

    private function recordAudit(int $userId, string $email, string $action, string $entityType, string $entityId, ?array $old, ?array $new): void {
        try {
            $pdo = Database::getConnection();
            $stmt = $pdo->prepare("
                INSERT INTO audit_logs (user_id, user_email, organization_id, action, entity_type, entity_id, ip_address, old_values, new_values)
                VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?)
            ");
            $stmt->execute([
                $userId,
                $email,
                $action,
                $entityType,
                $entityId,
                $_SERVER['REMOTE_ADDR'] ?? '127.0.0.1',
                $old ? json_encode($old) : null,
                $new ? json_encode($new) : null
            ]);
        } catch (\Exception $e) {
            // Silently continue audit recording
        }
    }

    private function json(array $data, int $status = 200): void {
        http_response_code($status);
        header('Content-Type: application/json');
        echo json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
        exit;
    }
}
