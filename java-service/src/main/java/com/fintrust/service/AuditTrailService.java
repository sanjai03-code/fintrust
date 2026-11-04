package com.fintrust.service;

import com.fintrust.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuditTrailService {

    public void log(Integer userId, String email, Integer orgId, String action, String entityType, String entityId, String oldVal, String newVal, String ip) {
        String sql = "INSERT INTO audit_logs (user_id, user_email, organization_id, action, entity_type, entity_id, ip_address, old_values, new_values) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (userId != null) ps.setInt(1, userId); else ps.setNull(1, java.sql.Types.INTEGER);
            ps.setString(2, email);
            if (orgId != null) ps.setInt(3, orgId); else ps.setNull(3, java.sql.Types.INTEGER);
            ps.setString(4, action);
            ps.setString(5, entityType);
            ps.setString(6, entityId);
            ps.setString(7, ip != null ? ip : "127.0.0.1");
            ps.setString(8, oldVal);
            ps.setString(9, newVal);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("[AuditTrailService] Error writing audit log: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getLogs(String entityType, String action, int limit) {
        List<Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT a.*, u.full_name AS user_name, o.name AS organization_name " +
                "FROM audit_logs a " +
                "LEFT JOIN users u ON a.user_id = u.id " +
                "LEFT JOIN organizations o ON a.organization_id = o.id WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();

        if (entityType != null && !entityType.trim().isEmpty()) {
            sql.append("AND a.entity_type = ? ");
            params.add(entityType);
        }
        if (action != null && !action.trim().isEmpty()) {
            sql.append("AND a.action = ? ");
            params.add(action);
        }
        sql.append("ORDER BY a.id DESC LIMIT ").append(limit > 0 ? limit : 100);

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("user_id", rs.getObject("user_id"));
                    map.put("user_email", rs.getString("user_email"));
                    map.put("user_name", rs.getString("user_name"));
                    map.put("organization_id", rs.getObject("organization_id"));
                    map.put("organization_name", rs.getString("organization_name"));
                    map.put("action", rs.getString("action"));
                    map.put("entity_type", rs.getString("entity_type"));
                    map.put("entity_id", rs.getString("entity_id"));
                    map.put("ip_address", rs.getString("ip_address"));
                    map.put("old_values", rs.getString("old_values"));
                    map.put("new_values", rs.getString("new_values"));
                    map.put("created_at", rs.getString("created_at"));
                    list.add(map);
                }
            }
        } catch (Exception e) {
            System.err.println("[AuditTrailService] Error fetching audit logs: " + e.getMessage());
        }
        return list;
    }
}
