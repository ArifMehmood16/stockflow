package dev.stockflow.inventory.web;

import dev.stockflow.inventory.application.StockLookup;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public final class StockController {
  private final StockLookup lookup;

  public StockController(StockLookup lookup) {
    this.lookup = lookup;
  }

  public record StockResponse(
      String code,
      String productName,
      int available,
      long version,
      int tenantId,
      int bucket,
      String source,
      Instant observedAt) {}

  @GetMapping("/v1/catalog/{code}/stock")
  public StockResponse stock(@PathVariable String code) {
    var stock =
        lookup.find(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    return new StockResponse(
        stock.code(),
        stock.productName(),
        stock.available(),
        stock.version(),
        stock.tenantId(),
        stock.bucket(),
        "postgresql-primary",
        Instant.now());
  }
}
