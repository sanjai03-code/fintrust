<?php
declare(strict_types=1);

namespace FinTrust\Middleware;

use FinTrust\Config\JwtHandler;

class AuthMiddleware {
    public static function authenticate(): ?array {
        $headers = getallheaders();
        $authHeader = $headers['Authorization'] ?? $headers['authorization'] ?? '';

        if (!preg_match('/Bearer\s+(.*)$/i', $authHeader, $matches)) {
            self::respondUnauthorized('Authorization token missing or malformed');
            return null;
        }

        $token = trim($matches[1]);
        $payload = JwtHandler::decode($token);

        if (!$payload) {
            self::respondUnauthorized('Invalid or expired authentication token');
            return null;
        }

        return $payload;
    }

    public static function authorizeRoles(array $allowedRoles, ?array $user): bool {
        if (!$user) {
            self::respondUnauthorized('Unauthenticated');
            return false;
        }

        $userRole = $user['role'] ?? '';
        if ($userRole === 'ADMIN') {
            return true; // Admin has superuser access
        }

        if (!in_array($userRole, $allowedRoles, true)) {
            http_response_code(403);
            header('Content-Type: application/json');
            echo json_encode([
                'success' => false,
                'error' => 'Forbidden: Insufficient privileges for role ' . $userRole
            ]);
            exit;
        }

        return true;
    }

    private static function respondUnauthorized(string $message): void {
        http_response_code(401);
        header('Content-Type: application/json');
        echo json_encode([
            'success' => false,
            'error' => $message
        ]);
        exit;
    }
}
