package dev.stockflow.inventory;

import static org.junit.jupiter.api.Assertions.*;

import dev.stockflow.inventory.application.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RunStockHttpTest.Fixture.class)
class RunStockHttpTest {
  static final UUID RUN = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
  static final UUID TENANT = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2");
  static final UUID OTHER = UUID.fromString("dddddddd-dddd-4ddd-8ddd-ddddddddddd4");
  static final UUID WAREHOUSE = UUID.fromString("cccccccc-cccc-4ccc-8ccc-ccccccccccc3");
  static final UUID RESERVATION = UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee5");
  static final byte[] KEY = new byte[32];
  @LocalServerPort int port;

  static class FakeRun implements RunInventory {
    public ScopeToken.TenantScope authenticate(String authorization) {
      if (authorization == null || !authorization.startsWith("Bearer ")) throw new SecurityException();
      try {
        var scope = ScopeToken.verifyTenant(authorization.substring(7), KEY, Instant.now());
        if (!RUN.equals(scope.runId())) throw new SecurityException();
        return scope;
      } catch (IllegalArgumentException invalid) { throw new SecurityException(); }
    }
    public ScopeToken.SessionScope verifySession(String token, ScopeToken.TenantScope tenant) {
      try {
        var scope = ScopeToken.verifySession(token, KEY, Instant.now());
        if (!scope.runId().equals(tenant.runId()) || !scope.tenantId().equals(tenant.tenantId()))
          throw new SecurityException();
        return scope;
      } catch (IllegalArgumentException invalid) { throw new SecurityException(); }
    }
    public Optional<RunStock> stock(UUID tenant, UUID warehouse, String sku) {
      return TENANT.equals(tenant) && WAREHOUSE.equals(warehouse) && "00123".equals(sku)
          ? Optional.of(new RunStock(sku, 17, 4)) : Optional.empty();
    }
    public Optional<ReservationView> reservation(UUID tenant, UUID id) {
      return TENANT.equals(tenant) && RESERVATION.equals(id)
          ? Optional.of(new ReservationView(id, "ACTIVE", 2, 4)) : Optional.empty();
    }
    public OperationResponse reserve(UUID tenant, UUID warehouse, String sku, int quantity,
        String key) {
      return new OperationResponse(201, "{\"id\":\"" + RESERVATION + "\"}", false);
    }
    public OperationResponse release(UUID tenant, UUID id, String key) {
      return new OperationResponse(200, "{\"state\":\"RELEASED\"}", false);
    }
  }
  @TestConfiguration static class Fixture {
    @Bean @Primary FakeRun runInventory() { return new FakeRun(); }
  }

  HttpResponse<String> get(String path, String token, String session) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    if (token != null) request.header("Authorization", "Bearer " + token);
    if (session != null) request.header("X-Session-Token", session);
    try (var client = HttpClient.newHttpClient()) {
      return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
  }

  @Test void readScopeAndConsistencyAreEnforced() throws Exception {
    String good = ScopeToken.issueTenant(RUN, TENANT, Instant.now().plusSeconds(300), KEY);
    String other = ScopeToken.issueTenant(RUN, OTHER, Instant.now().plusSeconds(300), KEY);
    String wrongRun = ScopeToken.issueTenant(UUID.randomUUID(), TENANT, Instant.now().plusSeconds(300), KEY);
    String path = "/v1/warehouses/" + WAREHOUSE + "/stock/00123";
    assertTrue(get(path, good, null).body().contains("\"available\":17"));
    assertEquals(404, get(path, other, null).statusCode());
    assertEquals(401, get(path, wrongRun, null).statusCode());
    assertEquals(401, get(path, good + "x", null).statusCode());
    assertEquals(401, get(path, null, null).statusCode());
    assertEquals(409, get(path + "?consistency=eventual", good, null).statusCode());
    assertEquals(400, get(path + "?consistency=other", good, null).statusCode());
    String session = ScopeToken.issueSession(RUN, TENANT, WAREHOUSE, "00123", 4,
        Instant.now().plusSeconds(300), KEY);
    assertEquals(200, get(path + "?consistency=session", good, session).statusCode());
    assertEquals(401, get(path + "?consistency=session", good, null).statusCode());
    assertEquals(404, get("/v1/reservations/" + RESERVATION, other, null).statusCode());
    assertEquals(200, get("/v1/reservations/" + RESERVATION, good, null).statusCode());
  }

  @Test void authenticatedReserveRouteIsTheOnlyOpenedMutation() throws Exception {
    String token = ScopeToken.issueTenant(RUN, TENANT, Instant.now().plusSeconds(300), KEY);
    try (var client = HttpClient.newHttpClient()) {
      var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/reservations"))
          .header("Authorization", "Bearer " + token).header("Idempotency-Key", "reserve-1")
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("{\"warehouseId\":\"" + WAREHOUSE
              + "\",\"sku\":\"00123\",\"quantity\":2}")).build();
      assertEquals(201, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
  }

  @Test void releaseRouteRequiresTheSameTenantCredential() throws Exception {
    String token = ScopeToken.issueTenant(RUN, TENANT, Instant.now().plusSeconds(300), KEY);
    try (var client = HttpClient.newHttpClient()) {
      var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
          + "/v1/reservations/" + RESERVATION + "/release"))
          .header("Authorization", "Bearer " + token)
          .header("Idempotency-Key", "release-1")
          .POST(HttpRequest.BodyPublishers.noBody()).build();
      assertEquals(200, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
  }

  @Test void bodylessReleaseWithoutContentLengthIsAllowed() throws Exception {
    String token = ScopeToken.issueTenant(RUN, TENANT, Instant.now().plusSeconds(300), KEY);
    try (var socket = new java.net.Socket("127.0.0.1", port)) {
      socket.setSoTimeout(3000);
      String request = "POST /v1/reservations/" + RESERVATION + "/release HTTP/1.1\r\n"
          + "Host: 127.0.0.1\r\nAuthorization: Bearer " + token
          + "\r\nIdempotency-Key: release-no-body\r\nConnection: close\r\n\r\n";
      socket.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      String response = new String(socket.getInputStream().readAllBytes(),
          java.nio.charset.StandardCharsets.UTF_8);
      assertTrue(response.startsWith("HTTP/1.1 200"), response);
    }
  }
}
