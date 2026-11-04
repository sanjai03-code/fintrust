<?php
declare(strict_types=1);

namespace FinTrust\Controllers;

use FinTrust\Config\Database;
use FinTrust\Config\S3Storage;
use PDO;

class DocumentController {
    public function listDocuments(): void {
        $pdo = Database::getConnection();
        $sql = "
            SELECT 
                d.*,
                o.name AS organization_name,
                u.full_name AS uploaded_by_name,
                t.transaction_ref
            FROM documents d
            LEFT JOIN organizations o ON d.organization_id = o.id
            LEFT JOIN users u ON d.user_id = u.id
            LEFT JOIN transactions t ON d.transaction_id = t.id
            ORDER BY d.id DESC
        ";
        $stmt = $pdo->query($sql);
        $docs = $stmt->fetchAll();

        $this->json(['success' => true, 'documents' => $docs]);
    }

    public function uploadDocument(array $currentUser): void {
        if (!isset($_FILES['file']) || $_FILES['file']['error'] !== UPLOAD_ERR_OK) {
            $this->json(['success' => false, 'error' => 'No file uploaded or upload error occurred'], 400);
            return;
        }

        $file = $_FILES['file'];
        $orgId = (int)($_POST['organization_id'] ?? 1);
        $txId = !empty($_POST['transaction_id']) ? (int)$_POST['transaction_id'] : null;
        $category = strtoupper(trim($_POST['document_category'] ?? 'INVOICE'));

        $s3 = new S3Storage();
        $s3Meta = $s3->putObject($file['tmp_name'], $file['name'], $orgId);

        $pdo = Database::getConnection();
        $stmt = $pdo->prepare("
            INSERT INTO documents (
                organization_id, user_id, transaction_id, file_name, file_type, 
                file_size, storage_path, s3_bucket, s3_key, checksum, document_category, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'STORED')
        ");
        $stmt->execute([
            $orgId,
            $currentUser['user_id'],
            $txId,
            $s3Meta['file_name'],
            $s3Meta['file_type'],
            $s3Meta['file_size'],
            $s3Meta['storage_path'],
            $s3Meta['s3_bucket'],
            $s3Meta['s3_key'],
            $s3Meta['checksum'],
            $category
        ]);
        $newDocId = (int)$pdo->lastInsertId();

        // Record Audit Trail
        $this->recordAudit($currentUser, 'DOCUMENT_UPLOADED', 'DOCUMENT', (string)$newDocId, $orgId, null, [
            'file_name' => $s3Meta['file_name'],
            'checksum' => $s3Meta['checksum'],
            's3_key' => $s3Meta['s3_key']
        ]);

        $this->json([
            'success' => true,
            'message' => 'Document uploaded to S3 vault successfully',
            'document' => array_merge(['id' => $newDocId], $s3Meta)
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
