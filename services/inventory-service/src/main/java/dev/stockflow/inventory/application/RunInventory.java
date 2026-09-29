package dev.stockflow.inventory.application;

import java.util.Optional;
import java.util.UUID;

public interface RunInventory {
  ScopeToken.TenantScope authenticate(String authorization);
  ScopeToken.SessionScope verifySession(String token, ScopeToken.TenantScope tenant);
  Optional<RunStock> stock(UUID tenant, UUID warehouse, String sku);
  Optional<ReservationView> reservation(UUID tenant, UUID id);
  OperationResponse reserve(UUID tenant, UUID warehouse, String sku, int quantity, String key);

  record ReservationView(UUID id, String state, int quantity, long stockVersion) {}
  record OperationResponse(int status, String body, boolean replayed) {}
}
