package com.fintrust.config;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class JwtValidator {

    private static String getSecret() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.trim().isEmpty()) {
            secret = "fintrust_super_secure_jwt_shared_secret_key_2026_xyz987654321";
        }
        return secret;
    }

    public static Map<String, Object> validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }

        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            return null;
        }

        String headerB64 = parts[0];
        String payloadB64 = parts[1];
        String signatureB64 = parts[2];

        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(secretKey);

            String dataToSign = headerB64 + "." + payloadB64;
            byte[] signatureBytes = hmac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
            String expectedSigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

            if (!MessageDigest.isEqual(expectedSigB64.getBytes(StandardCharsets.UTF_8), signatureB64.getBytes(StandardCharsets.UTF_8))) {
                return null; // Invalid signature
            }

            // Decode payload
            byte[] decodedPayload = Base64.getUrlDecoder().decode(payloadB64);
            String payloadJson = new String(decodedPayload, StandardCharsets.UTF_8);

            return parseJsonMap(payloadJson);
        } catch (Exception e) {
            System.err.println("[JwtValidator] Validation error: " + e.getMessage());
            return null;
        }
    }

    private static Map<String, Object> parseJsonMap(String json) {
        Map<String, Object> map = new HashMap<>();
        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
            String[] tokens = trimmed.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String token : tokens) {
                String[] pair = token.split(":", 2);
                if (pair.length == 2) {
                    String key = pair[0].trim().replaceAll("^\"|\"$", "");
                    String val = pair[1].trim().replaceAll("^\"|\"$", "");
                    map.put(key, val);
                }
            }
        }
        return map;
    }
}
