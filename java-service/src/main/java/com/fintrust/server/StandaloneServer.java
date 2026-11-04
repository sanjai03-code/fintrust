package com.fintrust.server;

import com.fintrust.config.JwtValidator;
import com.fintrust.service.AuditTrailService;
import com.fintrust.service.LedgerVerificationService;
import com.fintrust.service.RiskEngineService;
import com.fintrust.service.TransactionService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StandaloneServer {

    private static final TransactionService transactionService = new TransactionService();
    private static final LedgerVerificationService ledgerService = new LedgerVerificationService();
    private static final RiskEngineService riskService = new RiskEngineService();
    private static final AuditTrailService auditService = new AuditTrailService();

    public static void main(String[] args) throws IOException {
        int p = 8080;
        String portEnv = System.getenv("JAVA_SERVICE_PORT");
        if (portEnv != null && !portEnv.trim().isEmpty()) {
            p = Integer.parseInt(portEnv.trim());
        }
        final int port = p;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        System.out.println("=================================================");
        System.out.println(" FinTrust Java Backend Microservice (JDK 21)");
        System.out.println(" Transaction Processing, Ledger, Risk & Audit");
        System.out.println(" Listening on: http://127.0.0.1:" + port);
        System.out.println("=================================================");

        // Health
        server.createContext("/api/health", exchange -> {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            String resp = "{\"status\":\"UP\",\"service\":\"FinTrust Java Service\",\"version\":\"2.0.0\",\"jdk\":\"21\",\"port\":" + port + "}";
            sendJsonResponse(exchange, 200, resp);
        });

        // Transactions Handler
        server.createContext("/api/transactions", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                addCorsHeaders(exchange);
                String method = exchange.getRequestMethod();
                if ("OPTIONS".equalsIgnoreCase(method)) { exchange.sendResponseHeaders(200, -1); return; }

                Map<String, Object> user = authenticate(exchange);
                if (user == null) return;

                String uri = exchange.getRequestURI().toString();

                if ("GET".equalsIgnoreCase(method)) {
                    Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
                    Integer orgId = query.containsKey("organization_id") ? Integer.parseInt(query.get("organization_id")) : null;
                    String status = query.get("status");
                    String risk = query.get("risk_level");

                    List<Map<String, Object>> txs = transactionService.getTransactions(orgId, status, risk);
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"service_source\":\"Java Microservice (Port " + port + ")\",\"transactions\":" + toJson(txs) + "}");
                    return;
                }

                if ("POST".equalsIgnoreCase(method)) {
                    String body = readRequestBody(exchange);
                    Map<String, Object> data = parseJson(body);
                    Map<String, Object> created = transactionService.createTransaction(data, user);
                    if (created != null) {
                        sendJsonResponse(exchange, 201, "{\"success\":true,\"message\":\"Transaction created\",\"transaction\":" + toJson(created) + "}");
                    } else {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"Failed to create transaction\"}");
                    }
                    return;
                }

                if ("PUT".equalsIgnoreCase(method) && uri.matches(".*/\\d+/status")) {
                    String[] parts = uri.split("/");
                    int txId = Integer.parseInt(parts[parts.length - 2]);
                    String body = readRequestBody(exchange);
                    Map<String, Object> data = parseJson(body);
                    String status = (String) data.get("status");
                    boolean ok = transactionService.updateStatus(txId, status, user);
                    sendJsonResponse(exchange, ok ? 200 : 404, "{\"success\":" + ok + "}");
                    return;
                }

                sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        });

        // Ledger Handler
        server.createContext("/api/ledger", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                addCorsHeaders(exchange);
                String method = exchange.getRequestMethod();
                if ("OPTIONS".equalsIgnoreCase(method)) { exchange.sendResponseHeaders(200, -1); return; }

                Map<String, Object> user = authenticate(exchange);
                if (user == null) return;

                String uri = exchange.getRequestURI().getPath();

                if ("GET".equalsIgnoreCase(method)) {
                    List<Map<String, Object>> entries = ledgerService.getLedgerEntries();
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"entries\":" + toJson(entries) + "}");
                    return;
                }

                if ("POST".equalsIgnoreCase(method) && (uri.endsWith("/verify") || uri.endsWith("/ledger"))) {
                    String body = readRequestBody(exchange);
                    Map<String, Object> data = parseJson(body);
                    Integer txId = data.containsKey("transaction_id") && !data.get("transaction_id").toString().isEmpty()
                            ? Integer.parseInt(data.get("transaction_id").toString()) : null;
                    boolean autoReconcile = data.containsKey("auto_reconcile") && Boolean.parseBoolean(data.get("auto_reconcile").toString());

                    List<Map<String, Object>> res = ledgerService.verifyAndReconcile(txId, autoReconcile, user);
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"verification_report\":" + toJson(res) + "}");
                    return;
                }

                sendJsonResponse(exchange, 404, "{\"error\":\"Not Found\"}");
            }
        });

        // Risk Handler
        server.createContext("/api/risk", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                addCorsHeaders(exchange);
                String method = exchange.getRequestMethod();
                if ("OPTIONS".equalsIgnoreCase(method)) { exchange.sendResponseHeaders(200, -1); return; }

                Map<String, Object> user = authenticate(exchange);
                if (user == null) return;

                String path = exchange.getRequestURI().getPath();

                if ("GET".equalsIgnoreCase(method) && path.contains("alerts")) {
                    Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
                    String status = query.get("status");
                    List<Map<String, Object>> alerts = riskService.getAlerts(status);
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"alerts\":" + toJson(alerts) + "}");
                    return;
                }

                if ("POST".equalsIgnoreCase(method) && path.matches(".*/alerts/\\d+/resolve")) {
                    String[] parts = path.split("/");
                    int alertId = Integer.parseInt(parts[parts.length - 2]);
                    String body = readRequestBody(exchange);
                    Map<String, Object> data = parseJson(body);
                    String status = data.getOrDefault("status", "RESOLVED").toString();
                    String notes = data.getOrDefault("resolution_notes", "Reviewed").toString();
                    boolean ok = riskService.resolveAlert(alertId, status, notes, user);
                    sendJsonResponse(exchange, ok ? 200 : 404, "{\"success\":" + ok + "}");
                    return;
                }

                if ("POST".equalsIgnoreCase(method) && path.contains("evaluate")) {
                    String body = readRequestBody(exchange);
                    Map<String, Object> data = parseJson(body);
                    BigDecimal amount = new BigDecimal(data.getOrDefault("amount", "0").toString());
                    String country = (String) data.getOrDefault("destination_country", "USA");
                    String type = (String) data.getOrDefault("type", "TRANSFER");
                    String src = (String) data.getOrDefault("source_account", "");
                    String dst = (String) data.getOrDefault("destination_account", "");

                    Map<String, Object> eval = riskService.evaluateRisk(amount, country, type, src, dst);
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"risk_evaluation\":" + toJson(eval) + "}");
                    return;
                }

                sendJsonResponse(exchange, 404, "{\"error\":\"Not Found\"}");
            }
        });

        // Audit Handler
        server.createContext("/api/audit", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                addCorsHeaders(exchange);
                String method = exchange.getRequestMethod();
                if ("OPTIONS".equalsIgnoreCase(method)) { exchange.sendResponseHeaders(200, -1); return; }

                Map<String, Object> user = authenticate(exchange);
                if (user == null) return;

                if ("GET".equalsIgnoreCase(method)) {
                    Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
                    String entityType = query.get("entity_type");
                    String action = query.get("action");
                    int limit = query.containsKey("limit") ? Integer.parseInt(query.get("limit")) : 100;

                    List<Map<String, Object>> logs = auditService.getLogs(entityType, action, limit);
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"audit_logs\":" + toJson(logs) + "}");
                    return;
                }

                sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        });

        server.setExecutor(null);
        server.start();
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
    }

    private static Map<String, Object> authenticate(HttpExchange exchange) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            sendJsonResponse(exchange, 401, "{\"success\":false,\"error\":\"Missing Authorization Bearer token\"}");
            return null;
        }
        String token = auth.substring(7);
        Map<String, Object> user = JwtValidator.validateToken(token);
        if (user == null) {
            sendJsonResponse(exchange, 401, "{\"success\":false,\"error\":\"Invalid or expired JWT token\"}");
            return null;
        }
        return user;
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, int status, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String param : query.split("&")) {
            String[] entry = param.split("=", 2);
            if (entry.length == 2) map.put(entry[0], entry[1]);
        }
        return map;
    }

    private static Map<String, Object> parseJson(String json) {
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

    private static String toJson(Object obj) {
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
