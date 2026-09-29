package dev.stockflow.inventory;

import static org.junit.jupiter.api.Assertions.*;

import dev.stockflow.inventory.application.*;
import dev.stockflow.inventory.domain.Stock;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(StockHttpTest.Fixture.class)
class StockHttpTest {
  @LocalServerPort int port;
  @Autowired FakeRepository repository;

  static class FakeRepository implements StockRepository {
    boolean up = true;

    public Optional<Stock> find(String code) {
      if (!up)
        throw new StockUnavailable(new IllegalStateException("password=secret SQL SELECT private"));
      return code.equals("001234") ? Optional.of(StockLookupTest.ITEM) : Optional.empty();
    }

    public boolean ready() {
      return up;
    }
  }

  @TestConfiguration
  static class Fixture {
    @Bean
    @Primary
    FakeRepository fakeRepository() {
      return new FakeRepository();
    }
  }

  @BeforeEach
  void reset() {
    repository.up = true;
  }

  HttpResponse<String> get(String path) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
              .timeout(Duration.ofSeconds(5))
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }

  @Test
  void returnsStockWithMeasuredReadProvenance() throws Exception {
    var response = get("/v1/catalog/001234/stock");
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("\"code\":\"001234\""));
    assertTrue(response.body().contains("\"available\":17"));
    assertTrue(response.body().contains("\"source\":\"postgresql-primary\""));
    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
  }

  @Test
  void rejectsMalformedAndUnknownCodes() throws Exception {
    assertEquals(400, get("/v1/catalog/abc/stock").statusCode());
    assertEquals(404, get("/v1/catalog/999/stock").statusCode());
  }

  @Test
  void remainsLiveButNotReadyWhenDatabaseIsUnavailable() throws Exception {
    repository.up = false;
    assertEquals(200, get("/health/live").statusCode());
    assertEquals(503, get("/health/ready").statusCode());
    var response = get("/v1/catalog/001234/stock");
    assertEquals(503, response.statusCode());
    assertTrue(response.body().contains("STOCK_UNAVAILABLE"));
    assertFalse(response.body().contains("secret"));
    assertFalse(response.body().contains("SELECT"));
    assertTrue(response.body().contains("requestId"));
  }

  @Test
  void readyWithDatabaseAndDoesNotAllowBrowserCrossOriginReads() throws Exception {
    var response = get("/health/ready");
    assertEquals(200, response.statusCode());
    assertFalse(response.headers().firstValue("Access-Control-Allow-Origin").isPresent());
  }

  @Test
  void rejectsMutatingMethods() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var response =
          client.send(
              HttpRequest.newBuilder(
                      URI.create("http://127.0.0.1:" + port + "/v1/catalog/001234/stock"))
                  .POST(HttpRequest.BodyPublishers.ofString("{}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(405, response.statusCode());
      assertTrue(response.body().contains("METHOD_NOT_ALLOWED"));
    }
  }

  @Test
  void rejectsReboundHostnames() throws Exception {
    try (var socket = new java.net.Socket("127.0.0.1", port)) {
      socket.setSoTimeout(3000);
      socket
          .getOutputStream()
          .write(
              "GET /health/live HTTP/1.1\r\nHost: untrusted.example\r\nConnection: close\r\n\r\n"
                  .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      String response =
          new String(
              socket.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      assertTrue(response.startsWith("HTTP/1.1 400"), response);
      assertTrue(response.contains("INVALID_HOST"));
    }
  }
}
