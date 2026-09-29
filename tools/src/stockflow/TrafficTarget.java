package stockflow;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.IntSupplier;
import java.util.regex.Pattern;

/** Fixed loopback inventory target; browser input cannot supply an address. */
final class TrafficTarget implements TrafficRun.Operation {
  private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-f-]{36})\"");
  private static final Pattern AVAILABLE = Pattern.compile("\"available\"\\s*:\\s*(\\d+)");
  private static final Pattern VERSION = Pattern.compile("\"version\"\\s*:\\s*(\\d+)");
  private final HttpClient client = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(2)).build();
  private final IntSupplier port;
  private final Supplier<String> token;
  private final UUID warehouse;
  private final String sku;

  TrafficTarget(int port, String token, UUID warehouse, String sku) {
    this(port, () -> token, warehouse, sku);
  }

  TrafficTarget(int port, Supplier<String> token, UUID warehouse, String sku) {
    this(() -> port, token, warehouse, sku);
  }

  TrafficTarget(IntSupplier port, Supplier<String> token, UUID warehouse, String sku) {
    if (!sku.matches("[0-9]{1,32}"))
      throw new IllegalArgumentException("Invalid owned inventory target.");
    this.port = port;
    this.token = token;
    this.warehouse = warehouse;
    this.sku = sku;
  }

  private HttpResponse<String> send(String path, String key, String body) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port.getAsInt() + path))
        .timeout(Duration.ofSeconds(3)).header("Authorization", "Bearer " + token.get());
    if (key != null) request.header("Idempotency-Key", key);
    if (body == null) request.GET();
    else request.header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body));
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String stockPath() { return "/v1/warehouses/" + warehouse + "/stock/" + sku; }

  private static long number(Pattern pattern, String body) {
    var match = pattern.matcher(body);
    if (!match.find()) throw new IllegalStateException("Inventory response missing stock field.");
    return Long.parseLong(match.group(1));
  }

  @Override public TrafficRun.Observation perform() throws Exception {
    var before = send(stockPath(), null, null);
    if (before.statusCode() != 200) throw new IllegalStateException("Inventory read returned " + before.statusCode());
    var reserved = send("/v1/reservations", UUID.randomUUID().toString(),
        "{\"warehouseId\":\"" + warehouse + "\",\"sku\":\"" + sku
            + "\",\"quantity\":1}");
    if (reserved.statusCode() != 201 && reserved.statusCode() != 200)
      throw new IllegalStateException("Inventory reservation returned " + reserved.statusCode());
    var id = ID.matcher(reserved.body());
    if (!id.find()) throw new IllegalStateException("Inventory reservation ID missing.");
    var released = send("/v1/reservations/" + id.group(1) + "/release",
        UUID.randomUUID().toString(), "");
    if (released.statusCode() != 200)
      throw new IllegalStateException("Inventory release returned " + released.statusCode());
    var after = send(stockPath(), null, null);
    if (after.statusCode() != 200) throw new IllegalStateException("Inventory read returned " + after.statusCode());
    return new TrafficRun.Observation("read → reserve → release → read",
        Math.toIntExact(number(AVAILABLE, after.body())), number(VERSION, after.body()));
  }
}
