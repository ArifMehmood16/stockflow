package dev.stockflow.inventory.web;

import dev.stockflow.inventory.application.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public final class RunStockController {
  private final RunInventory inventory;

  public RunStockController(RunInventory inventory) { this.inventory = inventory; }

  public record StockResponse(String sku, int available, long version, String source,
      Instant cachedAt, Instant observedAt) {}

  @GetMapping("/v1/warehouses/{warehouseId}/stock/{sku}")
  public StockResponse stock(@RequestHeader(value = "Authorization", required = false) String token,
      @RequestHeader(value = "X-Session-Token", required = false) String session,
      @PathVariable UUID warehouseId, @PathVariable String sku,
      @RequestParam(defaultValue = "primary") String consistency) {
    var scope = inventory.authenticate(token);
    if ("eventual".equals(consistency)) throw new RunHttpError(409, "CAPABILITY_UNAVAILABLE", false);
    if (!"primary".equals(consistency) && !"session".equals(consistency))
      throw new RunHttpError(400, "INVALID_CONSISTENCY", false);
    ScopeToken.SessionScope verified = null;
    if ("session".equals(consistency)) {
      verified = inventory.verifySession(session, scope);
      if (!warehouseId.equals(verified.warehouseId()) || !sku.equals(verified.sku()))
        throw new SecurityException("Session scope mismatch.");
    }
    var stock = inventory.stock(scope.tenantId(), warehouseId, sku)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (verified != null && stock.version() < verified.minVersion())
      throw new StockUnavailable(new IllegalStateException("Session version unavailable."));
    return new StockResponse(stock.sku(), stock.available(), stock.version(),
        "postgresql-primary", null, Instant.now());
  }

  @GetMapping("/v1/reservations/{id}")
  public RunInventory.ReservationView reservation(
      @RequestHeader(value = "Authorization", required = false) String token,
      @PathVariable UUID id) {
    var scope = inventory.authenticate(token);
    return inventory.reservation(scope.tenantId(), id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
}
