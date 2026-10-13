package dev.dialogapi.plugin.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal HTTP server (JDK built-in) that serves the generated pack zip to clients. */
final class PackHost {

    private final HttpServer server;
    private final ExecutorService executor;
    private final String bindAddress;
    private final int port;
    private volatile byte[] data = new byte[0];
    private volatile String sha1 = "";

    PackHost(String bindAddress, int port) throws IOException {
        this.bindAddress = bindAddress;
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(bindAddress, port), 16);
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "DialogAPI-PackHost");
            thread.setDaemon(true);
            return thread;
        });
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    boolean matches(String bindAddress, int port) {
        return this.bindAddress.equals(bindAddress) && this.port == port;
    }

    void update(byte[] data, String sha1) {
        this.data = data;
        this.sha1 = sha1;
    }

    void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            boolean head = method.equalsIgnoreCase("HEAD");
            if (!head && !method.equalsIgnoreCase("GET")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] body = data;
            if (body.length == 0) {
                exchange.sendResponseHeaders(503, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("ETag", "\"" + sha1 + "\"");
            exchange.sendResponseHeaders(200, head ? -1 : body.length);
            if (!head) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        }
    }
}
