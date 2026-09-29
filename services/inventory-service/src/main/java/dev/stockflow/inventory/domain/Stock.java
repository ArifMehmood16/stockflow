package dev.stockflow.inventory.domain;

public record Stock(
    String code, String productName, int available, long version, int tenantId, int bucket) {}
