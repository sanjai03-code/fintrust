package com.fintrust.servlet;

import com.fintrust.config.JwtValidator;
import com.fintrust.service.RiskEngineService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/risk/*")
public class RiskAnalysisServlet extends HttpServlet {

    private final RiskEngineService riskService = new RiskEngineService();

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

        String path = req.getPathInfo();
        if (path != null && path.contains("alerts")) {
            String status = req.getParameter("status");
            List<Map<String, Object>> alerts = riskService.getAlerts(status);

            resp.setContentType("application/json");
            PrintWriter out = resp.getWriter();
            out.println("{\"success\":true,\"alerts\":" + toJson(alerts) + "}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCORS(resp);
        Map<String, Object> user = authenticate(req, resp);
        if (user == null) return;

        String path = req.getPathInfo();
        String body = readBody(req);
        Map<String, Object> data = parseJson(body);

        if (path != null && path.matches("/alerts/\\d+/resolve")) {
            String idStr = path.split("/")[2];
            int alertId = Integer.parseInt(idStr);
            String status = data.containsKey("status") ? data.get("status").toString() : "RESOLVED";
            String notes = data.containsKey("resolution_notes") ? data.get("resolution_notes").toString() : "Reviewed";

            boolean ok = riskService.resolveAlert(alertId, status, notes, user);
            resp.setContentType("application/json");
            PrintWriter out = resp.getWriter();
            if (ok) {
                out.println("{\"success\":true,\"message\":\"Alert resolved successfully\"}");
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.println("{\"success\":false,\"error\":\"Alert not found\"}");
            }
            return;
        }

        if (path != null && path.contains("evaluate")) {
            BigDecimal amount = new BigDecimal(data.getOrDefault("amount", "0").toString());
            String country = (String) data.getOrDefault("destination_country", "USA");
            String type = (String) data.getOrDefault("type", "TRANSFER");
            String src = (String) data.getOrDefault("source_account", "");
            String dst = (String) data.getOrDefault("destination_account", "");

            Map<String, Object> eval = riskService.evaluateRisk(amount, country, type, src, dst);
            resp.setContentType("application/json");
            PrintWriter out = resp.getWriter();
            out.println("{\"success\":true,\"risk_evaluation\":" + toJson(eval) + "}");
        }
    }

    private void setCORS(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
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

    private String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private Map<String, Object> parseJson(String json) {
        Map<String, Object> map = new HashMap<>();
        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
            String[] tokens = trimmed.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String token : tokens) {
                String[] pair = token.split(":", 2);
                if (pair.length == 2) {
                    map.put(pair[0].trim().replaceAll("^\"|\"$", ""), pair[1].trim().replaceAll("^\"|\"$", ""));
                }
            }
        }
        return map;
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
