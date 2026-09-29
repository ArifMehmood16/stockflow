package dev.stockflow.inventory;

import static org.junit.jupiter.api.Assertions.*;

import dev.stockflow.inventory.application.ScopeToken;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScopeTokenTest {
  static final byte[] KEY = new byte[32];
  static final UUID RUN = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
  static final UUID TENANT = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2");
  static final UUID WAREHOUSE = UUID.fromString("cccccccc-cccc-4ccc-8ccc-ccccccccccc3");
  static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

  @Test
  void tenantCredentialRejectsTamperingExpiryAndWrongType() {
    String token = ScopeToken.issueTenant(RUN, TENANT, NOW.plusSeconds(60), KEY);
    assertEquals(TENANT, ScopeToken.verifyTenant(token, KEY, NOW).tenantId());
    assertThrows(IllegalArgumentException.class, () -> ScopeToken.verifyTenant(token, KEY, NOW.plusSeconds(60)));
    assertThrows(IllegalArgumentException.class, () -> ScopeToken.verifyTenant(token + "x", KEY, NOW));
    assertThrows(IllegalArgumentException.class, () -> ScopeToken.verifySession(token, KEY, NOW));
  }

  @Test
  void sessionCredentialBindsRunTenantWarehouseSkuAndVersion() {
    String token = ScopeToken.issueSession(RUN, TENANT, WAREHOUSE, "00123", 7,
        NOW.plusSeconds(60), KEY);
    var scope = ScopeToken.verifySession(token, KEY, NOW);
    assertEquals(RUN, scope.runId());
    assertEquals(TENANT, scope.tenantId());
    assertEquals(WAREHOUSE, scope.warehouseId());
    assertEquals("00123", scope.sku());
    assertEquals(7, scope.minVersion());
    assertThrows(IllegalArgumentException.class, () -> ScopeToken.issueSession(
        RUN, TENANT, WAREHOUSE, "bad-sku", 7, NOW.plusSeconds(60), KEY));
    assertThrows(IllegalArgumentException.class, () -> ScopeToken.issueSession(
        RUN, TENANT, WAREHOUSE, "00123", -1, NOW.plusSeconds(60), KEY));
  }

  @Test
  void malformedOrUntrustedCredentialNeverVerifies() {
    String valid = ScopeToken.issueTenant(RUN, TENANT, NOW.plusSeconds(60), KEY);
    assertThrows(IllegalArgumentException.class, () -> ScopeToken.verifyTenant(null, KEY, NOW));
    assertThrows(IllegalArgumentException.class, () -> ScopeToken.verifyTenant("", KEY, NOW));
    assertThrows(IllegalArgumentException.class, () -> ScopeToken.verifyTenant(
        "wrong." + valid.substring(4), KEY, NOW));
    assertThrows(IllegalArgumentException.class, () -> ScopeToken.verifyTenant(
        valid.substring(0, valid.lastIndexOf('.') + 1) + "not-a-signature", KEY, NOW));
    byte[] otherKey = new byte[32];
    otherKey[0] = 1;
    assertThrows(IllegalArgumentException.class, () -> ScopeToken.verifyTenant(valid, otherKey, NOW));
    assertThrows(IllegalArgumentException.class, () -> ScopeToken.verifyTenant(
        "x".repeat(1025), KEY, NOW));
  }
}
