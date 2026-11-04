package com.fintrust.service;

import com.fintrust.config.DatabaseConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class RiskEngineService {

    private final AuditTrailService auditService = new AuditTrailService();

    public Map<String, Object> evaluateRisk(BigDecimal amount, String country, String type, String src, String dst) {
        double score = 5.0; // Base score
        List<String> rules = new ArrayList<>();
        Map<String, Double> factors = new HashMap<>();
        factors.put("amount_risk", 0.0);
        factors.put("jurisdiction_risk", 0.0);
        factors.put("velocity_structuring_risk", 0.0);
        factors.put("corridor_risk", 0.0);

        String upperCountry = country != null ? country.toUpperCase().trim() : "USA";
        List<String> sanctioned = Arrays.asList("RUS", "IRN", "PRK", "SYR", "CUB", "BLR");
        List<String> taxHavens = Arrays.asList("CYM", "PAN", "VGB", "BMU", "BHS", "MCO");

        // Rule 1: Jurisdiction
        if (sanctioned.contains(upperCountry)) {
            score += 85.0;
            factors.put("jurisdiction_risk", 85.0);
            rules.add("OFAC/FATF High-Risk Sanctions Embargo Watchlist (" + upperCountry + ")");
        } else if (taxHavens.contains(upperCountry)) {
            score += 35.0;
            factors.put("jurisdiction_risk", 35.0);
            rules.add("Offshore Secrecy Jurisdiction / Tax Haven (" + upperCountry + ")");
        }

        // Rule 2: Structuring / CTR Evasion ($9,000 - $9,999)
        double amtVal = amount != null ? amount.doubleValue() : 0.0;
        if (amtVal >= 9000.0 && amtVal <= 9999.0) {
            score += 45.0;
            factors.put("velocity_structuring_risk", 45.0);
            rules.add("Structuring Pattern: Amount $" + String.format("%.2f", amtVal) + " just below $10,000 CTR reporting threshold");
        }

        // Rule 3: High Value Wire
        if (amtVal >= 200000.0) {
            score += 25.0;
            factors.put("amount_risk", 25.0);
            rules.add("Ultra-High Value Wire (> $200,000.00)");
        } else if (amtVal >= 50000.0) {
            score += 15.0;
            factors.put("amount_risk", 15.0);
            rules.add("Substantial Transfer Amount (> $50,000.00)");
        }

        // Rule 4: High Cash Withdrawal
        if ("WITHDRAWAL".equalsIgnoreCase(type) && amtVal > 10000.0) {
            score += 20.0;
            factors.put("corridor_risk", 20.0);
            rules.add("High-Volume Cash Withdrawal anomaly");
        }

        double totalScore = Math.min(100.0, Math.max(0.0, score));
        BigDecimal finalScore = BigDecimal.valueOf(totalScore).setScale(2, RoundingMode.HALF_UP);

        String tier = "LOW";
        if (totalScore >= 80.0) tier = "CRITICAL";
        else if (totalScore >= 60.0) tier = "HIGH";
        else if (totalScore >= 35.0) tier = "MEDIUM";

        if (rules.isEmpty()) {
            rules.add("Standard transaction profile verified");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("score", finalScore);
        result.put("tier", tier);
        result.put("factors", factors);
        result.put("rules", rules);
        return result;
    }

    public List<Map<String, Object>> getAlerts(String status) {
        List<Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT a.*, t.transaction_ref, t.amount, t.currency, t.source_account, t.destination_account, " +
                "o.name AS organization_name, u.full_name AS resolved_by_name " +
                "FROM alerts a " +
                "JOIN organizations o ON a.organization_id = o.id " +
                "LEFT JOIN transactions t ON a.transaction_id = t.id " +
                "LEFT JOIN users u ON a.resolved_by = u.id WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();
        if (status != null && !status.trim().isEmpty()) {
            sql.append("AND a.status = ? ");
            params.add(status.toUpperCase().trim());
        }
        sql.append("ORDER BY CASE a.severity WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END ASC, a.id DESC");

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("alert_code", rs.getString("alert_code"));
                    map.put("transaction_id", rs.getObject("transaction_id"));
                    map.put("transaction_ref", rs.getString("transaction_ref"));
                    map.put("amount", rs.getBigDecimal("amount"));
                    map.put("currency", rs.getString("currency"));
                    map.put("source_account", rs.getString("source_account"));
                    map.put("destination_account", rs.getString("destination_account"));
                    map.put("organization_id", rs.getInt("organization_id"));
                    map.put("organization_name", rs.getString("organization_name"));
                    map.put("severity", rs.getString("severity"));
                    map.put("title", rs.getString("title"));
                    map.put("description", rs.getString("description"));
                    map.put("status", rs.getString("status"));
                    map.put("resolution_notes", rs.getString("resolution_notes"));
                    map.put("resolved_by_name", rs.getString("resolved_by_name"));
                    map.put("created_at", rs.getString("created_at"));
                    list.add(map);
                }
            }
        } catch (Exception e) {
            System.err.println("[RiskEngineService] Error fetching alerts: " + e.getMessage());
        }
        return list;
    }

    public boolean resolveAlert(int alertId, String newStatus, String notes, Map<String, Object> user) {
        String selectSql = "SELECT * FROM alerts WHERE id = ?";
        String updateSql = "UPDATE alerts SET status = ?, resolution_notes = ?, resolved_by = ?, resolved_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement selPs = conn.prepareStatement(selectSql)) {
            selPs.setInt(1, alertId);
            try (ResultSet rs = selPs.executeQuery()) {
                if (!rs.next()) return false;
                String oldStatus = rs.getString("status");
                int orgId = rs.getInt("organization_id");

                try (PreparedStatement updPs = conn.prepareStatement(updateSql)) {
                    updPs.setString(1, newStatus);
                    updPs.setString(2, notes);
                    if (user != null && user.containsKey("user_id")) {
                        updPs.setInt(3, Integer.parseInt(user.get("user_id").toString()));
                    } else {
                        updPs.setNull(3, java.sql.Types.INTEGER);
                    }
                    updPs.setInt(4, alertId);
                    updPs.executeUpdate();
                }

                Integer uId = user != null && user.containsKey("user_id") ? Integer.parseInt(user.get("user_id").toString()) : null;
                String email = user != null ? (String) user.get("email") : null;
                auditService.log(uId, email, orgId, "ALERT_RESOLVED", "ALERT", String.valueOf(alertId),
                        "{\"status\":\"" + oldStatus + "\"}",
                        "{\"status\":\"" + newStatus + "\",\"notes\":\"" + notes + "\"}", "127.0.0.1");

                return true;
            }
        } catch (Exception e) {
            System.err.println("[RiskEngineService] Error resolving alert: " + e.getMessage());
            return false;
        }
    }
}
