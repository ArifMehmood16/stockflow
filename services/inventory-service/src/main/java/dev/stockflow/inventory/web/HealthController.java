package dev.stockflow.inventory.web;

import dev.stockflow.inventory.application.StockRepository;
import dev.stockflow.inventory.application.RunInventory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public final class HealthController {
  private final StockRepository repository;
  private final RunInventory run;

  public HealthController(StockRepository repository, RunInventory run) {
    this.repository = repository;
    this.run = run;
  }

  public record Health(String status) {}

  @GetMapping("/health/live")
  public Health live() {
    return new Health("UP");
  }

  @GetMapping("/health/ready")
  public ResponseEntity<Health> ready() {
    boolean ready = repository.ready() && run.ready();
    return ResponseEntity.status(ready ? 200 : 503).body(new Health(ready ? "UP" : "DOWN"));
  }
}
