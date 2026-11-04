package com.fintrust.servlet;

import com.fintrust.config.JwtValidator;
import com.fintrust.service.TransactionService;
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

@WebServlet("/api/transactions/*")
public class TransactionServlet extends HttpServlet {

    private final TransactionService transactionService = new TransactionService();

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

        String orgParam = req.getParameter("organization_id");
        Integer orgId = (orgParam != null && !orgParam.isEmpty()) ? Integer.parseInt(orgParam) : null;
        String status = req.getParameter("status");
        String risk = req.getParameter("risk_level");

        List<Map<String, Object>> list = transactionService.getTransactions(orgId, status, risk);

        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();
        out.println("{\"success\":true,\"service_source\":\"Java Jakarta Servlet (Tomcat/Standalone)\",\"transactions\":" + toJson(list) + "}");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCORS(resp);
        Map<String, Object> user = authenticate(req, resp);
        if (user == null) return;

        String body = readBody(req);
        Map<String, Object> data = parseJson(body);

        Map<String, Object> created = transactionService.createTransaction(data, user);
        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();

        if (created != null) {
            resp.setStatus(HttpServletResponse.SC_CREATED);
            out.println("{\"success\":true,\"message\":\"Transaction created successfully\",\"transaction\":" + toJson(created) + "}");
        } else {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.println("{\"success\":false,\"error\":\"Failed to create transaction\"}");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCORS(resp);
        Map<String, Object> user = authenticate(req, resp);
        if (user == null) return;

        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.matches("/\\d+/status")) {
            String idStr = pathInfo.split("/")[1];
            int txId = Integer.parseInt(idStr);
            String body = readBody(req);
            Map<String, Object> data = parseJson(body);
            String newStatus = (String) data.get("status");

            boolean ok = transactionService.updateStatus(txId, newStatus, user);
            resp.setContentType("application/json");
            PrintWriter out = resp.getWriter();
            if (ok) {
                out.println("{\"success\":true,\"message\":\"Status updated successfully\"}");
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.println("{\"success\":false,\"error\":\"Transaction not found\"}");
            }
        }
    }

    private void setCORS(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
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
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
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
                    String k = pair[0].trim().replaceAll("^\"|\"$", "");
                    String v = pair[1].trim().replaceAll("^\"|\"$", "");
                    map.put(k, v);
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
                if (val instanceof Number || val instanceof Boolean) {
                    sb.append(val);
                } else if (val instanceof Map || val instanceof List) {
                    sb.append(toJson(val));
                } else {
                    sb.append("\"").append(val != null ? val.toString().replace("\"", "\\\"") : "").append("\"");
                }
                if (++i < map.size()) sb.append(",");
            }
            sb.append("}");
            return sb.toString();
        }
        return "\"" + obj.toString() + "\"";
    }
}
