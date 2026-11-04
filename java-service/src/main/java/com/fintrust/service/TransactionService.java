package com.fintrust.service;

import com.fintrust.config.DatabaseConfig;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

public class TransactionService {

    private final RiskEngineService riskService = new RiskEngineService();
    private final AuditTrailService auditService = new AuditTrailService();

    public List<Map<String, Object>> getTransactions(Integer orgId, String status, String riskLevel) {
        List<Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT t.*, o.name AS organization_name, u.full_name AS creator_name, " +
                "r.total_score, r.factors_breakdown " +
                "FROM transactions t " +
                "JOIN organizations o ON t.organization_id = o.id " +
                "LEFT JOIN users u ON t.user_id = u.id " +
                "LEFT JOIN risk_scores r ON t.id = r.transaction_id WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();

        if (orgId != null && orgId > 0) {
            sql.append("AND t.organization_id = ? ");
            params.add(orgId);
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append("AND t.status = ? ");
            params.add(status.toUpperCase().trim());
        }
        if (riskLevel != null && !riskLevel.trim().isEmpty()) {
            sql.append("AND t.risk_level = ? ");
            params.add(riskLevel.toUpperCase().trim());
        }
        sql.append("ORDER BY t.id DESC");

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("transaction_ref", rs.getString("transaction_ref"));
                    map.put("organization_id", rs.getInt("organization_id"));
                    map.put("organization_name", rs.getString("organization_name"));
                    map.put("user_id", rs.getObject("user_id"));
                    map.put("creator_name", rs.getString("creator_name"));
                    map.put("type", rs.getString("type"));
                    map.put("amount", rs.getBigDecimal("amount"));
                    map.put("currency", rs.getString("currency"));
                    map.put("source_account", rs.getString("source_account"));
                    map.put("destination_account", rs.getString("destination_account"));
                    map.put("destination_country", rs.getString("destination_country"));
                    map.put("description", rs.getString("description"));
                    map.put("status", rs.getString("status"));
                    map.put("risk_level", rs.getString("risk_level"));
                    map.put("total_score", rs.getBigDecimal("total_score"));
                    map.put("factors_breakdown", rs.getString("factors_breakdown"));
                    map.put("created_at", rs.getString("created_at"));
                    list.add(map);
                }
            }
        } catch (Exception e) {
            System.err.println("[TransactionService] Error fetching transactions: " + e.getMessage());
        }
        return list;
    }

    public Map<String, Object> createTransaction(Map<String, Object> data, Map<String, Object> user) {
        int orgId = data.containsKey("organization_id") ? Integer.parseInt(data.get("organization_id").toString()) : 1;
        String type = data.containsKey("type") ? data.get("type").toString().toUpperCase().trim() : "TRANSFER";
        BigDecimal amount = new BigDecimal(data.get("amount").toString());
        String currency = data.containsKey("currency") ? data.get("currency").toString().toUpperCase().trim() : "USD";
        String src = data.get("source_account").toString().trim();
        String dst = data.get("destination_account").toString().trim();
        String dstCountry = data.containsKey("destination_country") ? data.get("destination_country").toString().toUpperCase().trim() : "USA";
        String desc = data.containsKey("description") ? data.get("description").toString().trim() : "";

        String txRef = "TX-" + Calendar.getInstance().get(Calendar.YEAR) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // Evaluate Risk
        Map<String, Object> riskEval = riskService.evaluateRisk(amount, dstCountry, type, src, dst);
        BigDecimal score = (BigDecimal) riskEval.get("score");
        String tier = (String) riskEval.get("tier");
        @SuppressWarnings("unchecked")
        List<String> rules = (List<String>) riskEval.get("rules");

        String status = "PENDING";
        if (score.doubleValue() >= 75.0) {
            status = "FLAGGED";
        } else if (score.doubleValue() >= 50.0) {
            status = "UNDER_REVIEW";
        }

        String sqlTx = "INSERT INTO transactions (transaction_ref, organization_id, user_id, type, amount, currency, " +
                       "source_account, destination_account, destination_country, description, status, risk_level, risk_score_cache) " +
                       "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlTx, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, txRef);
            ps.setInt(2, orgId);
            if (user != null && user.containsKey("user_id")) {
                ps.setInt(3, Integer.parseInt(user.get("user_id").toString()));
            } else {
                ps.setNull(3, java.sql.Types.INTEGER);
            }
            ps.setString(4, type);
            ps.setBigDecimal(5, amount);
            ps.setString(6, currency);
            ps.setString(7, src);
            ps.setString(8, dst);
            ps.setString(9, dstCountry);
            ps.setString(10, desc);
            ps.setString(11, status);
            ps.setString(12, tier);
            ps.setBigDecimal(13, score);

            ps.executeUpdate();
            int txId = 0;
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    txId = rs.getInt(1);
                }
            }

            // Insert Double-Entry Ledger
            String sqlLedger = "INSERT INTO ledger_entries (transaction_id, entry_type, account_number, amount, currency, balance_after, is_verified, verification_notes) " +
                               "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement psL = conn.prepareStatement(sqlLedger)) {
                // Debit
                psL.setInt(1, txId);
                psL.setString(2, "DEBIT");
                psL.setString(3, src);
                psL.setBigDecimal(4, amount);
                psL.setString(5, currency);
                psL.setBigDecimal(6, new BigDecimal("5000000").subtract(amount));
                psL.setBoolean(7, "COMPLETED".equals(status));
                psL.setString(8, "Automated debit posting");
                psL.executeUpdate();

                // Credit
                psL.setInt(1, txId);
                psL.setString(2, "CREDIT");
                psL.setString(3, dst);
                psL.setBigDecimal(4, amount);
                psL.setString(5, currency);
                psL.setBigDecimal(6, amount);
                psL.setBoolean(7, "COMPLETED".equals(status));
                psL.setString(8, "Automated credit posting");
                psL.executeUpdate();
            }

            // Insert Risk Score Record
            String sqlRisk = "INSERT INTO risk_scores (transaction_id, organization_id, total_score, risk_tier, factors_breakdown, rule_matches) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement psR = conn.prepareStatement(sqlRisk)) {
                psR.setInt(1, txId);
                psR.setInt(2, orgId);
                psR.setBigDecimal(3, score);
                psR.setString(4, tier);
                psR.setString(5, riskEval.get("factors").toString());
                psR.setString(6, rules.toString());
                psR.executeUpdate();
            }

            // Generate Suspicious Alert if Risk >= 50
            if (score.doubleValue() >= 50.0) {
                String alertCode = "ALT-" + Calendar.getInstance().get(Calendar.YEAR) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                String sqlAlert = "INSERT INTO alerts (alert_code, transaction_id, organization_id, severity, title, description, status) VALUES (?, ?, ?, ?, ?, ?, 'NEW')";
                try (PreparedStatement psA = conn.prepareStatement(sqlAlert)) {
                    psA.setString(1, alertCode);
                    psA.setInt(2, txId);
                    psA.setInt(3, orgId);
                    psA.setString(4, tier);
                    psA.setString(5, "Java Risk Engine: " + rules.get(0));
                    psA.setString(6, "Suspicious pattern detected for " + txRef + " (Risk Score: " + score + "/100)");
                    psA.executeUpdate();
                }
            }

            // Audit
            Integer uId = user != null && user.containsKey("user_id") ? Integer.parseInt(user.get("user_id").toString()) : null;
            String email = user != null ? (String) user.get("email") : null;
            auditService.log(uId, email, orgId, "TRANSACTION_CREATED", "TRANSACTION", String.valueOf(txId), null,
                    "{\"ref\":\"" + txRef + "\",\"amount\":" + amount + ",\"risk_score\":" + score + "}", "127.0.0.1");

            Map<String, Object> res = new HashMap<>();
            res.put("id", txId);
            res.put("transaction_ref", txRef);
            res.put("status", status);
            res.put("risk_level", tier);
            res.put("risk_score", score);
            return res;

        } catch (Exception e) {
            System.err.println("[TransactionService] Creation error: " + e.getMessage());
            return null;
        }
    }

    public boolean updateStatus(int txId, String newStatus, Map<String, Object> user) {
        String selSql = "SELECT * FROM transactions WHERE id = ?";
        String updSql = "UPDATE transactions SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(selSql)) {
            ps.setInt(1, txId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String oldStatus = rs.getString("status");
                int orgId = rs.getInt("organization_id");

                try (PreparedStatement uPs = conn.prepareStatement(updSql)) {
                    uPs.setString(1, newStatus);
                    uPs.setInt(2, txId);
                    uPs.executeUpdate();
                }

                Integer uId = user != null && user.containsKey("user_id") ? Integer.parseInt(user.get("user_id").toString()) : null;
                String email = user != null ? (String) user.get("email") : null;
                auditService.log(uId, email, orgId, "TRANSACTION_STATUS_UPDATED", "TRANSACTION", String.valueOf(txId),
                        "{\"status\":\"" + oldStatus + "\"}", "{\"status\":\"" + newStatus + "\"}", "127.0.0.1");

                return true;
            }
        } catch (Exception e) {
            System.err.println("[TransactionService] Error updating status: " + e.getMessage());
            return false;
        }
    }
}
