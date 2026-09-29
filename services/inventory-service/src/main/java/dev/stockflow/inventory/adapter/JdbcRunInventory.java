package dev.stockflow.inventory.adapter;

import dev.stockflow.inventory.application.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class JdbcRunInventory implements RunInventory {
  private final UUID runId;
  private final String schema;
  private final String url;
  private final String user;
  private final String password;
  private final byte[] key;

  public JdbcRunInventory(@Value("${STOCKFLOW_ACTIVE_RUN_ID:}") String active,
      @Value("${STOCKFLOW_RUN_JDBC_URL:}") String url,
      @Value("${STOCKFLOW_RUN_JDBC_USER:}") String user,
      @Value("${STOCKFLOW_RUN_JDBC_PASSWORD:}") String password,
      @Value("${STOCKFLOW_RUN_KEY_FILE:}") String keyFile) throws Exception {
    if (active.isBlank()) {
      this.runId = null;
      this.schema = null;
      this.url = null;
      this.user = null;
      this.password = null;
      this.key = null;
      return;
    }
    this.runId = UUID.fromString(active);
    this.schema = "sf_run_" + runId.toString().replace("-", "");
    if (!user.equals("sf_w_" + runId.toString().replace("-", "")) || password.isBlank()
        || !url.startsWith("jdbc:postgresql://") || keyFile.isBlank())
      throw new IllegalStateException("Run service configuration is invalid.");
    this.url = url;
    this.user = user;
    this.password = password;
    Path file = Path.of(keyFile);
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
        || Files.getPosixFilePermissions(file).stream()
            .anyMatch(permission -> !permission.name().startsWith("OWNER_")))
      throw new IllegalStateException("Run credential key is not private.");
    this.key = Files.readAllBytes(file);
    if (key.length != 32) throw new IllegalStateException("Run credential key is invalid.");
  }

  @Override
  public ScopeToken.TenantScope authenticate(String authorization) {
    if (runId == null || authorization == null || !authorization.startsWith("Bearer "))
      throw new SecurityException("Run credential required.");
    try {
      var scope = ScopeToken.verifyTenant(authorization.substring(7), key, Instant.now());
      if (!runId.equals(scope.runId())) throw new SecurityException("Wrong run.");
      return scope;
    } catch (IllegalArgumentException invalid) {
      throw new SecurityException("Invalid run credential.");
    }
  }

  @Override
  public ScopeToken.SessionScope verifySession(String token, ScopeToken.TenantScope tenant) {
    try {
      var scope = ScopeToken.verifySession(token, key, Instant.now());
      if (!scope.runId().equals(tenant.runId()) || !scope.tenantId().equals(tenant.tenantId()))
        throw new SecurityException("Session scope mismatch.");
      return scope;
    } catch (IllegalArgumentException invalid) {
      throw new SecurityException("Invalid session token.");
    }
  }

  private Connection connect() throws SQLException {
    var properties = new Properties();
    properties.setProperty("user", user);
    properties.setProperty("password", password);
    properties.setProperty("connectTimeout", "2");
    properties.setProperty("socketTimeout", "3");
    return DriverManager.getConnection(url, properties);
  }

  @Override
  public Optional<RunStock> stock(UUID tenant, UUID warehouse, String sku) {
    if (!sku.matches("[0-9]{1,32}")) throw new IllegalArgumentException("Invalid SKU.");
    try (var connection = connect();
        var query = connection.prepareStatement("SELECT sku,available,version FROM " + schema
            + ".inventory WHERE tenant_id=? AND warehouse_id=? AND sku=?")) {
      query.setQueryTimeout(2);
      query.setObject(1, tenant);
      query.setObject(2, warehouse);
      query.setString(3, sku);
      try (var row = query.executeQuery()) {
        return row.next() ? Optional.of(new RunStock(row.getString(1), row.getInt(2), row.getLong(3)))
            : Optional.empty();
      }
    } catch (SQLException failure) {
      throw new StockUnavailable(failure);
    }
  }

  @Override
  public Optional<ReservationView> reservation(UUID tenant, UUID id) {
    try (var connection = connect();
        var query = connection.prepareStatement("SELECT id,state,quantity,stock_version FROM "
            + schema + ".reservation WHERE tenant_id=? AND id=?")) {
      query.setQueryTimeout(2);
      query.setObject(1, tenant);
      query.setObject(2, id);
      try (var row = query.executeQuery()) {
        return row.next() ? Optional.of(new ReservationView((UUID) row.getObject(1),
            row.getString(2), row.getInt(3), row.getLong(4))) : Optional.empty();
      }
    } catch (SQLException failure) {
      throw new StockUnavailable(failure);
    }
  }
}
