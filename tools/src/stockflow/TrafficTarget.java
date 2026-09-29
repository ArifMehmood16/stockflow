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

  private HttpResponse<String> send(String path) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port.getAsInt() + path))
        .timeout(Duration.ofSeconds(3)).header("Authorization", "Bearer " + token.get());
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String stockPath() { return "/v1/warehouses/" + warehouse + "/stock/" + sku; }

  private static long number(Pattern pattern, String body) {
    var match = pattern.matcher(body);
    if (!match.find()) throw new IllegalStateException("Inventory response missing stock field.");
    return Long.parseLong(match.group(1));
  }

  @Override public TrafficRun.Observation perform() throws Exception {
    var response = send(stockPath());
    if (response.statusCode() != 200)
      throw new IllegalStateException("Inventory read returned " + response.statusCode());
    return new TrafficRun.Observation("read → Java API → PostgreSQL",
        Math.toIntExact(number(AVAILABLE, response.body())), number(VERSION, response.body()));
  }
}
