package com.fintrust.servlet;

import com.fintrust.config.JwtValidator;
import com.fintrust.service.AuditTrailService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

@WebServlet("/api/audit/*")
public class AuditServlet extends HttpServlet {

    private final AuditTrailService auditService = new AuditTrailService();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCORS(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCORS(resp);
        Map<String, Object> user = authenticate(req, resp);
        if (user == null) return;

        String entityType = req.getParameter("entity_type");
        String action = req.getParameter("action");
        String limitStr = req.getParameter("limit");
        int limit = (limitStr != null && !limitStr.isEmpty()) ? Integer.parseInt(limitStr) : 100;

        List<Map<String, Object>> logs = auditService.getLogs(entityType, action, limit);

        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();
        out.println("{\"success\":true,\"service_source\":\"Java Jakarta Servlet\",\"audit_logs\":" + toJson(logs) + "}");
    }

    private void setCORS(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private Map<String, Object> authenticate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json");
            resp.getWriter().println("{\"success\":false,\"error\":\"Unauthorized: Missing bearer token\"}");
            return null;
        }
        String token = authHeader.substring(7);
        Map<String, Object> user = JwtValidator.validateToken(token);
        if (user == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json");
            resp.getWriter().println("{\"success\":false,\"error\":\"Unauthorized: Invalid or expired token\"}");
            return null;
        }
        return user;
    }

    private String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                sb.append(toJson(list.get(i)));
                if (i < list.size() - 1) sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sb.append("\"").append(e.getKey()).append("\":");
                Object val = e.getValue();
                if (val instanceof Number || val instanceof Boolean) sb.append(val);
                else if (val instanceof Map || val instanceof List) sb.append(toJson(val));
                else sb.append("\"").append(val != null ? val.toString().replace("\"", "\\\"") : "").append("\"");
                if (++i < map.size()) sb.append(",");
            }
            sb.append("}");
            return sb.toString();
        }
        return "\"" + obj.toString() + "\"";
    }
}
