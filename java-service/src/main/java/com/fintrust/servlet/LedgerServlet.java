package com.fintrust.servlet;

import com.fintrust.config.JwtValidator;
import com.fintrust.service.LedgerVerificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/ledger/*")
public class LedgerServlet extends HttpServlet {

    private final LedgerVerificationService ledgerService = new LedgerVerificationService();

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

        List<Map<String, Object>> entries = ledgerService.getLedgerEntries();
        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();
        out.println("{\"success\":true,\"service_source\":\"Java Jakarta Servlet\",\"entries\":" + toJson(entries) + "}");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCORS(resp);
        Map<String, Object> user = authenticate(req, resp);
        if (user == null) return;

        String path = req.getPathInfo();
        if (path == null || path.equals("/") || path.equals("/verify")) {
            String body = readBody(req);
            Map<String, Object> data = parseJson(body);

            Integer txId = data.containsKey("transaction_id") && !data.get("transaction_id").toString().isEmpty()
                    ? Integer.parseInt(data.get("transaction_id").toString()) : null;
            boolean autoReconcile = data.containsKey("auto_reconcile") && Boolean.parseBoolean(data.get("auto_reconcile").toString());

            List<Map<String, Object>> results = ledgerService.verifyAndReconcile(txId, autoReconcile, user);
            resp.setContentType("application/json");
            PrintWriter out = resp.getWriter();
            out.println("{\"success\":true,\"verification_report\":" + toJson(results) + "}");
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
