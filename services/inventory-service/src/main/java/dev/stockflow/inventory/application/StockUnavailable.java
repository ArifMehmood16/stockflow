package dev.stockflow.inventory.application;

public final class StockUnavailable extends RuntimeException {
  public StockUnavailable(Throwable cause) {
    super("Stock data is temporarily unavailable.", cause);
  }
}
