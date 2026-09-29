package stockflow;

import dev.stockflow.inventory.application.ScopeToken;
import java.net.URI;
import java.net.http.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Explicit native test: two owned JVMs, same run, one durable reservation. */
final class ScalingSmoke {
  static void run() throws Exception {
    var configured = new ProcessBuilder("true");
    Api.configure(configured);
    var env = configured.environment();
    UUID runId = UUID.fromString(env.get("STOCKFLOW_ACTIVE_RUN_ID"));
    var properties = new Properties();
    properties.setProperty("user", env.get("STOCKFLOW_RUN_JDBC_USER"));
    properties.setProperty("password", env.get("STOCKFLOW_RUN_JDBC_PASSWORD"));
    properties.setProperty("socketTimeout", "5");
    try (var connection = DriverManager.getConnection(env.get("STOCKFLOW_RUN_JDBC_URL"), properties);
        var query = connection.prepareStatement("SELECT tenant_id,warehouse_id,sku,available FROM "
            + RunFixture.schema(runId) + ".inventory WHERE available>=8 LIMIT 1");
        var rows = query.executeQuery(); var client = HttpClient.newHttpClient()) {
      if (!rows.next()) throw new IllegalStateException("Fixture requires available stock.");
      UUID tenant = rows.getObject(1, UUID.class), warehouse = rows.getObject(2, UUID.class);
      String sku = rows.getString(3);
      String token = ScopeToken.issueTenant(runId, tenant, Instant.now().plusSeconds(300),
          FixtureCredentials.key(runId));
      int primaryPort = Api.port();
      Process primary = Api.start();
      try (var pool = new ApiPool(primaryPort, Api::start)) {
        pool.add();
        String body = "{\"warehouseId\":\"" + warehouse + "\",\"sku\":\"" + sku + "\",\"quantity\":1}";
        String key = UUID.randomUUID().toString();
        var first = post(client, primaryPort, "/v1/reservations", token, key, body);
        var replay = post(client, primaryPort + 1, "/v1/reservations", token, key, body);
        if (first.statusCode() != 201 || replay.statusCode() != 201
            || !first.body().equals(replay.body())
            || !replay.headers().firstValue("Idempotency-Replayed").orElse("").equals("true"))
          throw new IllegalStateException("Cross-instance durable replay failed.");
        var id = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-f-]{36})\"").matcher(first.body());
        if (!id.find()) throw new IllegalStateException("Reservation ID missing.");
        var release = post(client, primaryPort + 1, "/v1/reservations/" + id.group(1) + "/release",
            token, UUID.randomUUID().toString(), "");
        if (release.statusCode() != 200) throw new IllegalStateException("Cross-instance release failed.");
        var target = new TrafficTarget(pool::nextPort, () -> token, warehouse, sku);
        var observed = target.perform();
        if (observed.available() != rows.getInt(4)
            || pool.primaryRequests.get() != 2 || pool.secondaryRequests.get() != 2)
          throw new IllegalStateException("Shared stock or routing check failed.");
        pool.remove();
        if (pool.instances() != 1 || pool.nextPort() != primaryPort)
          throw new IllegalStateException("Route removal failed.");
        System.out.println("PASS: two ready JVMs; identical cross-instance replay; release restores stock; "
            + "four requests split 2/2; extra JVM stopped and primary route retained.");
      } finally {
        primary.destroy();
        if (!primary.waitFor(7, TimeUnit.SECONDS))
          throw new IllegalStateException("Owned primary API did not stop.");
      }
    }
  }

  private static HttpResponse<String> post(HttpClient client, int port, String path,
      String token, String key, String body) throws Exception {
    return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
        .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + token)
        .header("Idempotency-Key", key).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
  }
}
