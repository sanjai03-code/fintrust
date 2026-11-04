<?php
declare(strict_types=1);

namespace FinTrust\Config;

class S3Storage {
    private string $bucket;
    private string $storageDir;

    public function __construct() {
        $this->bucket = getenv('S3_BUCKET') ?: 'fintrust-vault';
        $root = dirname(__DIR__, 2);
        $this->storageDir = $root . '/php-service/uploads/docs';
        if (!is_dir($this->storageDir)) {
            mkdir($this->storageDir, 0777, true);
        }
    }

    /**
     * Store file with S3 key metadata and SHA-256 checksum
     */
    public function putObject(string $tmpFilePath, string $originalName, int $orgId): array {
        $checksum = hash_file('sha256', $tmpFilePath);
        $size = filesize($tmpFilePath);
        $ext = pathinfo($originalName, PATHINFO_EXTENSION);
        $cleanName = preg_replace('/[^a-zA-Z0-9_\-\.]/', '_', $originalName);
        $uniqueName = uniqid("doc_{$orgId}_", true) . '.' . $ext;
        
        $destinationPath = $this->storageDir . '/' . $uniqueName;
        move_uploaded_file($tmpFilePath, $destinationPath);

        $datePrefix = date('Y/m');
        $s3Key = "org-{$orgId}/{$datePrefix}/{$uniqueName}";

        return [
            'file_name' => $originalName,
            'file_type' => mime_content_type($destinationPath) ?: 'application/octet-stream',
            'file_size' => $size,
            'storage_path' => 'uploads/docs/' . $uniqueName,
            's3_bucket' => $this->bucket,
            's3_key' => $s3Key,
            'checksum' => $checksum,
            's3_uri' => "s3://{$this->bucket}/{$s3Key}"
        ];
    }

    public function getStoragePath(string $relativePath): ?string {
        $root = dirname(__DIR__, 2);
        $fullPath = $root . '/php-service/' . ltrim($relativePath, '/');
        return file_exists($fullPath) ? $fullPath : null;
    }
}
