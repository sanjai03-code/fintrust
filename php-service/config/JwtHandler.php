<?php
declare(strict_types=1);

namespace FinTrust\Config;

class JwtHandler {
    private static function getSecret(): string {
        return getenv('JWT_SECRET') ?: 'fintrust_super_secure_jwt_shared_secret_key_2026_xyz987654321';
    }

    public static function encode(array $payload, int $ttl = 86400): string {
        $header = [
            'typ' => 'JWT',
            'alg' => 'HS256'
        ];

        $now = time();
        $payload['iat'] = $payload['iat'] ?? $now;
        $payload['exp'] = $payload['exp'] ?? ($now + $ttl);
        $payload['iss'] = $payload['iss'] ?? (getenv('JWT_ISSUER') ?: 'fintrust-auth-service');
        $payload['aud'] = $payload['aud'] ?? (getenv('JWT_AUDIENCE') ?: 'fintrust-microservices');

        $base64UrlHeader = self::base64UrlEncode(json_encode($header, JSON_UNESCAPED_SLASHES));
        $base64UrlPayload = self::base64UrlEncode(json_encode($payload, JSON_UNESCAPED_SLASHES));

        $signature = hash_hmac('sha256', "{$base64UrlHeader}.{$base64UrlPayload}", self::getSecret(), true);
        $base64UrlSignature = self::base64UrlEncode($signature);

        return "{$base64UrlHeader}.{$base64UrlPayload}.{$base64UrlSignature}";
    }

    public static function decode(string $token): ?array {
        $parts = explode('.', $token);
        if (count($parts) !== 3) {
            return null;
        }

        [$base64UrlHeader, $base64UrlPayload, $base64UrlSignature] = $parts;

        $expectedSig = hash_hmac('sha256', "{$base64UrlHeader}.{$base64UrlPayload}", self::getSecret(), true);
        $expectedBase64UrlSig = self::base64UrlEncode($expectedSig);

        if (!hash_equals($expectedBase64UrlSig, $base64UrlSignature)) {
            return null; // Invalid signature
        }

        $payloadJson = self::base64UrlDecode($base64UrlPayload);
        if (!$payloadJson) {
            return null;
        }

        $payload = json_decode($payloadJson, true);
        if (!is_array($payload)) {
            return null;
        }

        // Verify expiration
        if (isset($payload['exp']) && $payload['exp'] < time()) {
            return null; // Token expired
        }

        return $payload;
    }

    private static function base64UrlEncode(string $data): string {
        return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
    }

    private static function base64UrlDecode(string $data): string {
        $remainder = strlen($data) % 4;
        if ($remainder) {
            $data .= str_repeat('=', 4 - $remainder);
        }
        return base64_decode(strtr($data, '-_', '+/')) ?: '';
    }
}
