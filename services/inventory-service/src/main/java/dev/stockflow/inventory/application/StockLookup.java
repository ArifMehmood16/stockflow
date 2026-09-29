package dev.stockflow.inventory.application;

import dev.stockflow.inventory.domain.Stock;
import java.util.Optional;

public final class StockLookup {
  private final StockRepository repository;

  public StockLookup(StockRepository repository) {
    this.repository = repository;
  }

  public Optional<Stock> find(String code) {
    if (code == null || !code.matches("[0-9]{1,32}"))
      throw new IllegalArgumentException("Product code must contain 1–32 digits.");
    return repository.find(code);
  }
}
