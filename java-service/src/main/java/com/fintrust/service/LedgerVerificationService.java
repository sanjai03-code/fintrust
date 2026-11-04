package com.fintrust.service;

import com.fintrust.config.DatabaseConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LedgerVerificationService {

    private final AuditTrailService auditService = new AuditTrailService();

    public List<Map<String, Object>> getLedgerEntries() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT l.*, t.transaction_ref, t.organization_id, o.name AS organization_name, u.full_name AS verified_by_name " +
                     "FROM ledger_entries l " +
                     "JOIN transactions t ON l.transaction_id = t.id " +
                     "JOIN organizations o ON t.organization_id = o.id " +
                     "LEFT JOIN users u ON l.verified_by = u.id " +
                     "ORDER BY l.id DESC";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", rs.getInt("id"));
                map.put("transaction_id", rs.getInt("transaction_id"));
                map.put("transaction_ref", rs.getString("transaction_ref"));
                map.put("organization_name", rs.getString("organization_name"));
                map.put("entry_type", rs.getString("entry_type"));
                map.put("account_number", rs.getString("account_number"));
                map.put("amount", rs.getBigDecimal("amount"));
                map.put("currency", rs.getString("currency"));
                map.put("balance_after", rs.getBigDecimal("balance_after"));
                map.put("is_verified", rs.getBoolean("is_verified"));
                map.put("verified_at", rs.getString("verified_at"));
                map.put("verified_by_name", rs.getString("verified_by_name"));
                map.put("verification_notes", rs.getString("verification_notes"));
                map.put("created_at", rs.getString("created_at"));
                list.add(map);
            }
        } catch (Exception e) {
            System.err.println("[LedgerVerificationService] Error fetching ledger: " + e.getMessage());
        }
        return list;
    }

    public List<Map<String, Object>> verifyAndReconcile(Integer txId, boolean autoReconcile, Map<String, Object> user) {
        List<Map<String, Object>> results = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT t.id AS tx_id, t.transaction_ref, t.amount AS expected_amount, " +
                "COALESCE(SUM(CASE WHEN l.entry_type = 'DEBIT' THEN l.amount ELSE 0 END), 0) AS total_debit, " +
                "COALESCE(SUM(CASE WHEN l.entry_type = 'CREDIT' THEN l.amount ELSE 0 END), 0) AS total_credit, " +
                "COUNT(l.id) AS entry_count " +
                "FROM transactions t " +
                "LEFT JOIN ledger_entries l ON t.id = l.transaction_id "
        );
        if (txId != null && txId > 0) {
            sql.append("WHERE t.id = ? ");
        }
        sql.append("GROUP BY t.id, t.transaction_ref, t.amount");

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (txId != null && txId > 0) {
                ps.setInt(1, txId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int currentTxId = rs.getInt("tx_id");
                    String txRef = rs.getString("transaction_ref");
                    BigDecimal expectedAmt = rs.getBigDecimal("expected_amount");
                    BigDecimal totalDebit = rs.getBigDecimal("total_debit");
                    BigDecimal totalCredit = rs.getBigDecimal("total_credit");
                    int entryCount = rs.getInt("entry_count");

                    BigDecimal variance = totalDebit.subtract(totalCredit).setScale(2, RoundingMode.HALF_UP);
                    boolean isBalanced = (variance.compareTo(BigDecimal.ZERO) == 0) && (entryCount >= 2);

                    // Duplicate check
                    boolean isDuplicate = checkDuplicate(conn, currentTxId);

                    if (isBalanced && autoReconcile) {
                        String updateSql = "UPDATE ledger_entries SET is_verified = TRUE, verified_at = CURRENT_TIMESTAMP, " +
                                "verified_by = ?, verification_notes = 'Double-entry balanced and verified' WHERE transaction_id = ?";
                        try (PreparedStatement updPs = conn.prepareStatement(updateSql)) {
                            if (user != null && user.containsKey("user_id")) {
                                updPs.setInt(1, Integer.parseInt(user.get("user_id").toString()));
                            } else {
                                updPs.setNull(1, java.sql.Types.INTEGER);
                            }
                            updPs.setInt(2, currentTxId);
                            updPs.executeUpdate();
                        }
                    }

                    Map<String, Object> item = new HashMap<>();
                    item.put("transaction_id", currentTxId);
                    item.put("transaction_ref", txRef);
                    item.put("expected_amount", expectedAmt);
                    item.put("total_debit", totalDebit);
                    item.put("total_credit", totalCredit);
                    item.put("variance", variance);
                    item.put("is_balanced", isBalanced);
                    item.put("is_duplicate", isDuplicate);
                    item.put("status", isBalanced ? "BALANCED" : "IMBALANCE_DETECTED");
                    results.add(item);
                }
            }

            Integer uId = user != null && user.containsKey("user_id") ? Integer.parseInt(user.get("user_id").toString()) : null;
            String email = user != null ? (String) user.get("email") : null;
            auditService.log(uId, email, 1, "LEDGER_VERIFIED", "LEDGER",
                    txId != null ? String.valueOf(txId) : "BATCH", null,
                    "{\"reconciled_count\":" + results.size() + "}", "127.0.0.1");

        } catch (Exception e) {
            System.err.println("[LedgerVerificationService] Verification error: " + e.getMessage());
        }
        return results;
    }

    private boolean checkDuplicate(Connection conn, int txId) {
        String dupSql = "SELECT COUNT(*) AS c FROM transactions WHERE source_account = (SELECT source_account FROM transactions WHERE id = ?) " +
                        "AND destination_account = (SELECT destination_account FROM transactions WHERE id = ?) " +
                        "AND amount = (SELECT amount FROM transactions WHERE id = ?) AND id != ?";
        try (PreparedStatement ps = conn.prepareStatement(dupSql)) {
            ps.setInt(1, txId);
            ps.setInt(2, txId);
            ps.setInt(3, txId);
            ps.setInt(4, txId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("c") > 0;
                }
            }
        } catch (Exception e) {
            // continue
        }
        return false;
    }
}
