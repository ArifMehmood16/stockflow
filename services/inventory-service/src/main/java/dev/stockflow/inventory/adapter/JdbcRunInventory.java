package dev.stockflow.inventory.adapter;

import dev.stockflow.inventory.application.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public final class JdbcRunInventory implements RunInventory {
  private final UUID runId;
  private final String schema;
  private final String url;
  private final String user;
  private final String password;
  private final byte[] key;
  private final HikariDataSource writer;

  @Override
  public boolean ready() {
    if (runId == null) return true;
    try (var connection = connect();
        var query = connection.prepareStatement("SELECT 1 FROM " + schema + ".inventory LIMIT 1")) {
      query.setQueryTimeout(2);
      try (var row = query.executeQuery()) { return row.next(); }
    } catch (SQLException unavailable) { return false; }
  }

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
      this.writer = null;
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
    var pool = new HikariConfig();
    pool.setJdbcUrl(url);
    pool.setUsername(user);
    pool.setPassword(password);
    pool.setMaximumPoolSize(4);
    pool.setMinimumIdle(0);
    pool.setConnectionTimeout(2000);
    pool.setValidationTimeout(1000);
    pool.setInitializationFailTimeout(-1);
    pool.addDataSourceProperty("connectTimeout", "2");
    pool.addDataSourceProperty("socketTimeout", "3");
    pool.addDataSourceProperty("ApplicationName", "stockflow-run-writer");
    this.writer = new HikariDataSource(pool);
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
    return writer.getConnection();
  }

  @PreDestroy
  public void close() { if (writer != null) writer.close(); }

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

  @Override
  public OperationResponse reserve(UUID tenant, UUID warehouse, String sku, int quantity,
      String idempotencyKey) {
    String canonical = "{\"op\":\"reserve\",\"quantity\":" + quantity + ",\"sku\":\""
        + sku + "\",\"warehouseId\":\"" + warehouse + "\"}";
    String hash = sha256(canonical);
    try (var connection = connect()) {
      connection.setAutoCommit(false);
      try {
        try (var timeout = connection.createStatement()) {
          timeout.execute("SET LOCAL lock_timeout = '2s'");
          timeout.execute("SET LOCAL statement_timeout = '3s'");
        }
        Instant tokenExpiry = null;
        boolean claimed;
        try (var insert = connection.prepareStatement("INSERT INTO " + schema
            + ".idempotency_record (tenant_id,key,request_hash,state,expires_at)"
            + " VALUES (?,?,?,'CLAIMED',transaction_timestamp()+interval '24 hours')"
            + " ON CONFLICT (tenant_id,key) DO NOTHING RETURNING expires_at")) {
          insert.setObject(1, tenant);
          insert.setString(2, idempotencyKey);
          insert.setString(3, hash);
          try (var row = insert.executeQuery()) {
            claimed = row.next();
            if (claimed) tokenExpiry = row.getTimestamp(1).toInstant();
          }
        }
        if (!claimed) {
          try (var replay = connection.prepareStatement("SELECT request_hash,status_code,"
              + "response_json::text FROM " + schema
              + ".idempotency_record WHERE tenant_id=? AND key=?")) {
            replay.setObject(1, tenant);
            replay.setString(2, idempotencyKey);
            try (var row = replay.executeQuery()) {
              if (!row.next()) throw new RunFailure(409, "IDEMPOTENCY_IN_PROGRESS", true);
              if (!hash.equals(row.getString(1)))
                throw new RunFailure(409, "IDEMPOTENCY_CONFLICT", false);
              if (row.getObject(2) == null) throw new RunFailure(409, "IDEMPOTENCY_IN_PROGRESS", true);
              OperationResponse result = new OperationResponse(row.getInt(2), row.getString(3), true);
              connection.rollback();
              return result;
            }
          }
        }
        if (count(connection, "idempotency_record") > 200_000
            || count(connection, "reservation") >= 200_000)
          throw new RunFailure(429, "RUN_HISTORY_FULL", true);
        OperationResponse decision = reserveClaimed(connection, tenant, warehouse, sku, quantity,
            tokenExpiry);
        try (var complete = connection.prepareStatement("UPDATE " + schema
            + ".idempotency_record SET state='COMPLETED',status_code=?,response_json=?::jsonb"
            + " WHERE tenant_id=? AND key=? RETURNING response_json::text")) {
          complete.setInt(1, decision.status());
          complete.setString(2, decision.body());
          complete.setObject(3, tenant);
          complete.setString(4, idempotencyKey);
          try (var row = complete.executeQuery()) {
            if (!row.next()) throw new SQLException("Claim disappeared before completion.");
            decision = new OperationResponse(decision.status(), row.getString(1), false);
          }
        }
        connection.commit();
        return decision;
      } catch (SQLException failure) {
        connection.rollback();
        if ("55P03".equals(failure.getSQLState()))
          throw new RunFailure(409, "IDEMPOTENCY_IN_PROGRESS", true);
        throw failure;
      } catch (RuntimeException failure) {
        connection.rollback();
        throw failure;
      }
    } catch (SQLException failure) {
      throw new StockUnavailable(failure);
    }
  }

  private OperationResponse reserveClaimed(Connection connection, UUID tenant, UUID warehouse,
      String sku, int quantity, Instant tokenExpiry) throws SQLException {
    try (var update = connection.prepareStatement("UPDATE " + schema
        + ".inventory SET available=available-?,version=version+1,updated_at=clock_timestamp()"
        + " WHERE tenant_id=? AND warehouse_id=? AND sku=? AND available>=? RETURNING version")) {
      update.setInt(1, quantity);
      update.setObject(2, tenant);
      update.setObject(3, warehouse);
      update.setString(4, sku);
      update.setInt(5, quantity);
      try (var row = update.executeQuery()) {
        if (row.next()) {
          long version = row.getLong(1);
          UUID id = UUID.randomUUID();
          try (var insert = connection.prepareStatement("INSERT INTO " + schema
              + ".reservation (tenant_id,id,warehouse_id,sku,quantity,state,expires_at,stock_version)"
              + " VALUES (?,?,?,?,?,'ACTIVE',transaction_timestamp()+interval '30 seconds',?)")) {
            insert.setObject(1, tenant);
            insert.setObject(2, id);
            insert.setObject(3, warehouse);
            insert.setString(4, sku);
            insert.setInt(5, quantity);
            insert.setLong(6, version);
            insert.executeUpdate();
          }
          String session = ScopeToken.issueSession(runId, tenant, warehouse, sku, version,
              tokenExpiry, key);
          return new OperationResponse(201, "{\"id\":\"" + id + "\",\"state\":\"ACTIVE\","
              + "\"quantity\":" + quantity + ",\"stockVersion\":" + version
              + ",\"sessionToken\":\"" + session + "\"}", false);
        }
      }
    }
    try (var query = connection.prepareStatement("SELECT available,version FROM " + schema
        + ".inventory WHERE tenant_id=? AND warehouse_id=? AND sku=?")) {
      query.setObject(1, tenant);
      query.setObject(2, warehouse);
      query.setString(3, sku);
      try (var row = query.executeQuery()) {
        if (!row.next())
          return new OperationResponse(404, "{\"code\":\"STOCK_NOT_FOUND\","
              + "\"message\":\"Stock was not found.\",\"retryable\":false}", false);
        return new OperationResponse(409, "{\"code\":\"INSUFFICIENT_STOCK\","
            + "\"message\":\"Available stock is too low.\",\"retryable\":false,"
            + "\"available\":" + row.getInt(1) + ",\"version\":" + row.getLong(2) + "}", false);
      }
    }
  }

  private long count(Connection connection, String table) throws SQLException {
    try (var statement = connection.createStatement();
        var row = statement.executeQuery("SELECT count(*) FROM " + schema + "." + table)) {
      row.next();
      return row.getLong(1);
    }
  }

  private static String sha256(String value) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable.", impossible);
    }
  }

  @Override
  public OperationResponse release(UUID tenant, UUID id, String idempotencyKey) {
    String hash = sha256("{\"id\":\"" + id + "\",\"op\":\"release\"}");
    try (var connection = connect()) {
      connection.setAutoCommit(false);
      try {
        try (var timeout = connection.createStatement()) {
          timeout.execute("SET LOCAL lock_timeout = '2s'");
          timeout.execute("SET LOCAL statement_timeout = '3s'");
        }
        boolean claimed;
        try (var insert = connection.prepareStatement("INSERT INTO " + schema
            + ".idempotency_record (tenant_id,key,request_hash,state,expires_at)"
            + " VALUES (?,?,?,'CLAIMED',transaction_timestamp()+interval '24 hours')"
            + " ON CONFLICT (tenant_id,key) DO NOTHING RETURNING tenant_id")) {
          insert.setObject(1, tenant);
          insert.setString(2, idempotencyKey);
          insert.setString(3, hash);
          try (var row = insert.executeQuery()) { claimed = row.next(); }
        }
        if (!claimed) {
          try (var replay = connection.prepareStatement("SELECT request_hash,status_code,"
              + "response_json::text FROM " + schema
              + ".idempotency_record WHERE tenant_id=? AND key=?")) {
            replay.setObject(1, tenant);
            replay.setString(2, idempotencyKey);
            try (var row = replay.executeQuery()) {
              if (!row.next()) throw new RunFailure(409, "IDEMPOTENCY_IN_PROGRESS", true);
              if (!hash.equals(row.getString(1)))
                throw new RunFailure(409, "IDEMPOTENCY_CONFLICT", false);
              if (row.getObject(2) == null) throw new RunFailure(409, "IDEMPOTENCY_IN_PROGRESS", true);
              OperationResponse result = new OperationResponse(row.getInt(2), row.getString(3), true);
              connection.rollback();
              return result;
            }
          }
        }
        if (count(connection, "idempotency_record") > 200_000)
          throw new RunFailure(429, "RUN_HISTORY_FULL", true);
        OperationResponse decision = releaseClaimed(connection, tenant, id);
        try (var complete = connection.prepareStatement("UPDATE " + schema
            + ".idempotency_record SET state='COMPLETED',status_code=?,response_json=?::jsonb"
            + " WHERE tenant_id=? AND key=? RETURNING response_json::text")) {
          complete.setInt(1, decision.status());
          complete.setString(2, decision.body());
          complete.setObject(3, tenant);
          complete.setString(4, idempotencyKey);
          try (var row = complete.executeQuery()) {
            if (!row.next()) throw new SQLException("Claim disappeared before completion.");
            decision = new OperationResponse(decision.status(), row.getString(1), false);
          }
        }
        connection.commit();
        return decision;
      } catch (SQLException failure) {
        connection.rollback();
        if ("55P03".equals(failure.getSQLState()))
          throw new RunFailure(409, "IDEMPOTENCY_IN_PROGRESS", true);
        throw failure;
      } catch (RuntimeException failure) {
        connection.rollback();
        throw failure;
      }
    } catch (SQLException failure) {
      throw new StockUnavailable(failure);
    }
  }

  private OperationResponse releaseClaimed(Connection connection, UUID tenant, UUID id)
      throws SQLException {
    Long version = transition(connection, tenant, id, "RELEASED");
    String state = "RELEASED";
    if (version == null) {
      version = transition(connection, tenant, id, "EXPIRED");
      state = "EXPIRED";
    }
    if (version == null) {
      try (var query = connection.prepareStatement("SELECT state,stock_version FROM " + schema
          + ".reservation WHERE tenant_id=? AND id=?")) {
        query.setObject(1, tenant);
        query.setObject(2, id);
        try (var row = query.executeQuery()) {
          if (!row.next())
            return new OperationResponse(404, "{\"code\":\"RESERVATION_NOT_FOUND\","
                + "\"message\":\"Reservation was not found.\",\"retryable\":false}", false);
          state = row.getString(1);
          version = row.getLong(2);
        }
      }
    }
    return new OperationResponse(200, "{\"id\":\"" + id + "\",\"state\":\"" + state
        + "\",\"stockVersion\":" + version + "}", false);
  }

  private Long transition(Connection connection, UUID tenant, UUID id, String terminal)
      throws SQLException {
    String deadline = "EXPIRED".equals(terminal) ? "<= " : "> ";
    UUID warehouse;
    String sku;
    int quantity;
    try (var change = connection.prepareStatement("UPDATE " + schema
        + ".reservation SET state=?,terminal_at=transaction_timestamp()"
        + " WHERE tenant_id=? AND id=? AND state='ACTIVE' AND expires_at " + deadline
        + "transaction_timestamp() RETURNING warehouse_id,sku,quantity")) {
      change.setString(1, terminal);
      change.setObject(2, tenant);
      change.setObject(3, id);
      try (var row = change.executeQuery()) {
        if (!row.next()) return null;
        warehouse = (UUID) row.getObject(1);
        sku = row.getString(2);
        quantity = row.getInt(3);
      }
    }
    long version;
    try (var stock = connection.prepareStatement("UPDATE " + schema
        + ".inventory SET available=available+?,version=version+1,updated_at=clock_timestamp()"
        + " WHERE tenant_id=? AND warehouse_id=? AND sku=? RETURNING version")) {
      stock.setInt(1, quantity);
      stock.setObject(2, tenant);
      stock.setObject(3, warehouse);
      stock.setString(4, sku);
      try (var row = stock.executeQuery()) {
        if (!row.next()) throw new SQLException("Reservation stock row disappeared.");
        version = row.getLong(1);
      }
    }
    try (var saved = connection.prepareStatement("UPDATE " + schema
        + ".reservation SET stock_version=? WHERE tenant_id=? AND id=?")) {
      saved.setLong(1, version);
      saved.setObject(2, tenant);
      saved.setObject(3, id);
      saved.executeUpdate();
    }
    return version;
  }

  /** One bounded scan per tick; due rows remain durable across process restarts. */
  public int expireDue(int limit) {
    if (runId == null) return 0;
    var due = new ArrayList<UUID[]>();
    try (var connection = connect();
        var query = connection.prepareStatement("SELECT tenant_id,id FROM " + schema
            + ".reservation WHERE state='ACTIVE' AND expires_at<=transaction_timestamp()"
            + " ORDER BY expires_at LIMIT ?")) {
      query.setQueryTimeout(2);
      query.setInt(1, Math.min(Math.max(limit, 1), 20));
      try (var row = query.executeQuery()) {
        while (row.next()) due.add(new UUID[] {(UUID) row.getObject(1), (UUID) row.getObject(2)});
      }
    } catch (SQLException failure) {
      throw new StockUnavailable(failure);
    }
    int expired = 0;
    for (var item : due) {
      try (var connection = connect()) {
        connection.setAutoCommit(false);
        try {
          try (var timeout = connection.createStatement()) {
            timeout.execute("SET LOCAL lock_timeout = '100ms'");
            timeout.execute("SET LOCAL statement_timeout = '2s'");
          }
          if (transition(connection, item[0], item[1], "EXPIRED") != null) expired++;
          connection.commit();
        } catch (SQLException failure) {
          connection.rollback();
          if (!"55P03".equals(failure.getSQLState())) throw failure;
        }
      } catch (SQLException failure) {
        throw new StockUnavailable(failure);
      }
    }
    return expired;
  }

  @Scheduled(fixedDelay = 1000)
  public void expireTick() {
    if (runId == null) return;
    try {
      expireDue(20);
    } catch (StockUnavailable unavailable) {
      System.err.println("StockFlow expiry scan postponed; run database unavailable.");
    }
  }
}
