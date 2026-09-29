package dev.stockflow.inventory.application;

public record RunStock(String sku, int available, long version) {}
