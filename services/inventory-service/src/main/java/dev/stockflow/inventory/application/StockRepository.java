package dev.stockflow.inventory.application;

import dev.stockflow.inventory.domain.Stock;
import java.util.Optional;

public interface StockRepository {
  Optional<Stock> find(String code);

  boolean ready();
}
