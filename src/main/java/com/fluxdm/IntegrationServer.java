package com.fluxdm;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight HTTP server for browser-extension integration.
 * Binds to 127.0.0.1:9581 (loopback only) and exposes:
 *   GET  /api/ping     — health check
 *   POST /api/download — accept {"url":"..."} and forward to FluxDM
 */
public class IntegrationServer {

    private static final int PORT = 9581;
    private static final Pattern URL_PATTERN =
            Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    private HttpServer server;
    private final Consumer<String> onUrlReceived;

    public IntegrationServer(Consumer<String> onUrlReceived) {
        this.onUrlReceived = onUrlReceived;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/api/ping", this::handlePing);
            server.createContext("/api/download", this::handleDownload);
            server.setExecutor(null); // default single-thread executor
            server.start();
            System.out.println("IntegrationServer listening on 127.0.0.1:" + PORT);
        } catch (IOException e) {
            System.err.println("IntegrationServer: could not bind to port " + PORT
                    + " (" + e.getMessage() + "). Browser integration disabled.");
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("IntegrationServer stopped.");
        }
    }

    // ─── Handlers ────────────────────────────────────────────────────────────

    private void handlePing(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        sendJson(ex, 200, "{\"status\":\"running\"}");
    }

    private void handleDownload(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Matcher m = URL_PATTERN.matcher(body);
        if (!m.find()) {
            sendJson(ex, 400, "{\"error\":\"Missing or invalid 'url' field\"}");
            return;
        }

        String url = m.group(1);
        onUrlReceived.accept(url);
        sendJson(ex, 200, "{\"status\":\"ok\",\"url\":\"" + escapeJson(url) + "\"}");
    }

    // ─── CORS / Utility ──────────────────────────────────────────────────────

    /** Handles OPTIONS preflight. Returns true if the exchange was fully handled. */
    private boolean handleCors(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return true;
        }
        return false;
    }

    private void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
