package stockflow;

import com.sun.net.httpserver.*;
import dev.stockflow.inventory.application.ScopeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;

/** Loopback-only control for one owned fixture and one bounded experiment at a time. */
final class TrafficControl implements AutoCloseable {
  private final TrafficRun.Operation operation;
  private final ApiPool pool;
  private TrafficRun run;

  TrafficControl(TrafficRun.Operation operation) { this(operation, null); }
  TrafficControl(TrafficRun.Operation operation, ApiPool pool) {
    this.operation = operation;
    this.pool = pool;
  }

  static TrafficControl forSelectedRun() throws Exception {
    var configured = new ProcessBuilder("true");
    Api.configure(configured);
    var environment = configured.environment();
    UUID runId = UUID.fromString(environment.get("STOCKFLOW_ACTIVE_RUN_ID"));
    var properties = new Properties();
    properties.setProperty("user", environment.get("STOCKFLOW_RUN_JDBC_USER"));
    properties.setProperty("password", environment.get("STOCKFLOW_RUN_JDBC_PASSWORD"));
    properties.setProperty("connectTimeout", "3");
    properties.setProperty("socketTimeout", "3");
    try (var connection = DriverManager.getConnection(
            environment.get("STOCKFLOW_RUN_JDBC_URL"), properties);
        var query = connection.prepareStatement("SELECT tenant_id,warehouse_id,sku FROM "
            + RunFixture.schema(runId) + ".inventory WHERE available>=8 LIMIT 1");
        var rows = query.executeQuery()) {
      if (!rows.next()) throw new IllegalStateException("Owned fixture needs a stock row with at least 8 units.");
      UUID tenant = rows.getObject(1, UUID.class);
      UUID warehouse = rows.getObject(2, UUID.class);
      String sku = rows.getString(3);
      byte[] key = FixtureCredentials.key(runId);
      var pool = new ApiPool(Api.port(), Api::start);
      return new TrafficControl(new TrafficTarget(pool::nextPort,
          () -> ScopeToken.issueTenant(runId, tenant, Instant.now().plusSeconds(3600), key),
          warehouse, sku), pool);
    }
  }

  synchronized TrafficRun.Status start(TrafficRun.Limits limits) {
    if (run != null && (run.status().running() || run.status().inFlight() > 0))
      throw new IllegalStateException("Stop the active traffic run first.");
    if (run != null) run.close();
    run = new TrafficRun(limits, operation);
    run.start();
    return run.status();
  }

  synchronized TrafficRun.Status status() {
    return run == null ? new TrafficRun.Status(false, 0, 0, 0, 0, 0, 0, "", 0, 0, 0, "")
        : run.status();
  }

  synchronized TrafficRun.Status stop() {
    if (run != null) run.stop();
    return status();
  }

  @Override public synchronized void close() {
    if (run != null) run.close();
    if (pool != null) pool.close();
  }

  private static int parameter(String query, String name) {
    if (query == null) throw new IllegalArgumentException("Traffic limits are required.");
    for (String part : query.split("&")) {
      String[] entry = part.split("=", 2);
      if (entry.length == 2 && entry[0].equals(name)) return Integer.parseInt(entry[1]);
    }
    throw new IllegalArgumentException("Missing traffic limit: " + name);
  }

  private String json(TrafficRun.Status status) {
    return "{\"running\":" + status.running() + ",\"offered\":" + status.offered()
        + ",\"started\":" + status.started() + ",\"completed\":" + status.completed()
        + ",\"failed\":" + status.failed() + ",\"dropped\":" + status.dropped()
        + ",\"inFlight\":" + status.inFlight() + ",\"lastPath\":\""
        + status.lastPath() + "\",\"lastElapsedMillis\":" + status.lastElapsedMillis()
        + ",\"available\":" + status.available() + ",\"version\":"
        + status.version() + ",\"lastError\":\"" + status.lastError() + "\""
        + ",\"instances\":" + (pool == null ? 1 : pool.instances())
        + ",\"building\":" + (pool != null && pool.building())
        + ",\"primaryRequests\":" + (pool == null ? 0 : pool.primaryRequests.get())
        + ",\"secondaryRequests\":" + (pool == null ? 0 : pool.secondaryRequests.get()) + "}";
  }

  private static void reply(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
  }

  void attach(HttpServer server) {
    server.createContext("/lab/traffic", exchange -> {
      try {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if (!Set.of("/lab/traffic", "/lab/traffic/start", "/lab/traffic/stop",
            "/lab/traffic/add-instance", "/lab/traffic/remove-instance").contains(path)) {
          reply(exchange, 404, "{}");
          return;
        }
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String expected = "http://127.0.0.1:" + server.getAddress().getPort();
        String localhost = "http://localhost:" + server.getAddress().getPort();
        if (origin != null && !origin.equals(expected) && !origin.equals(localhost)) {
          reply(exchange, 403, "{}");
          return;
        }
        if (path.equals("/lab/traffic") && method.equals("GET"))
          reply(exchange, 200, json(status()));
        else if (path.equals("/lab/traffic/start") && method.equals("POST")) {
          try {
            String query = exchange.getRequestURI().getRawQuery();
            var limits = new TrafficRun.Limits(parameter(query, "rate"),
                parameter(query, "seconds"), parameter(query, "concurrency"));
            reply(exchange, 200, json(start(limits)));
          } catch (IllegalArgumentException error) {
            reply(exchange, 400, "{\"error\":\"INVALID_LIMITS\"}");
          } catch (IllegalStateException error) {
            reply(exchange, 409, "{\"error\":\"RUN_ACTIVE\"}");
          }
        } else if (path.equals("/lab/traffic/stop") && method.equals("POST"))
          reply(exchange, 200, json(stop()));
        else if (method.equals("POST") && path.endsWith("-instance")) {
          if (pool == null) { reply(exchange, 409, "{\"error\":\"CAPABILITY_UNAVAILABLE\"}"); return; }
          try {
            if (path.endsWith("/add-instance")) pool.add();
            else {
              synchronized (this) {
                if (status().running() || status().inFlight() > 0)
                  throw new IllegalStateException("Stop traffic before removing an instance.");
                pool.remove();
              }
            }
            reply(exchange, 200, json(status()));
          } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            reply(exchange, 409, "{\"error\":\"INSTANCE_CHANGE_FAILED\"}");
          }
        } else reply(exchange, 405, "{}");
      } finally {
        exchange.close();
      }
    });
  }
}
