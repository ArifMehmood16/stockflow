package dev.stockflow.inventory;

import static org.junit.jupiter.api.Assertions.*;

import dev.stockflow.inventory.adapter.JdbcRunInventory;
import dev.stockflow.inventory.application.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

/** Real PostgreSQL transaction check in a disposable database and role. */
class JdbcRunInventoryIT {
  @Test
  void reserveReplayConflictAndConcurrentStockConservation() throws Exception {
    String url = Objects.requireNonNull(System.getenv("STOCKFLOW_JDBC_URL"));
    var owner = new Properties();
    owner.setProperty("user", System.getenv("STOCKFLOW_JDBC_USER"));
    owner.setProperty("password", System.getenv("STOCKFLOW_JDBC_PASSWORD"));
    UUID run = UUID.randomUUID();
    UUID tenant = UUID.randomUUID(), other = UUID.randomUUID(), warehouse = UUID.randomUUID();
    String suffix = run.toString().replace("-", "");
    String schema = "sf_run_" + suffix, role = "sf_w_" + suffix;
    String database = "stockflow_test_" + suffix;
    String target = url.substring(0, url.lastIndexOf('/') + 1) + database;
    Path keyFile = Files.createTempFile("stockflow-run-key-", ".bin");
    Files.setPosixFilePermissions(keyFile,
        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    Files.write(keyFile, new byte[32]);
    try (var admin = DriverManager.getConnection(url, owner)) {
      try (var statement = admin.createStatement()) {
        statement.execute("CREATE DATABASE " + database);
        statement.execute("CREATE ROLE " + role + " LOGIN PASSWORD 'test-only-password'");
      }
      try {
        try (var fixture = DriverManager.getConnection(target, owner);
            var statement = fixture.createStatement()) {
          statement.execute("CREATE SCHEMA " + schema);
          statement.execute("SET search_path TO " + schema);
          statement.execute(Files.readString(Path.of("../../infra/migrations/002-run-schema.sql")));
          statement.execute("INSERT INTO " + schema
              + ".catalog_snapshot(code,product_name,brands,categories,source)"
              + " VALUES ('00123','Soup','','','test')");
          try (var insert = fixture.prepareStatement("INSERT INTO " + schema
              + ".inventory (tenant_id,warehouse_id,sku,available) VALUES (?,?,'00123',10)")) {
            insert.setObject(1, tenant);
            insert.setObject(2, warehouse);
            insert.executeUpdate();
          }
          statement.execute("GRANT USAGE ON SCHEMA " + schema + " TO " + role);
          statement.execute("GRANT SELECT,INSERT,UPDATE ON ALL TABLES IN SCHEMA " + schema
              + " TO " + role);
        }
        var inventory = new JdbcRunInventory(run.toString(), target, role,
            "test-only-password", keyFile.toString());
        assertTrue(inventory.stock(other, warehouse, "00123").isEmpty());
        var first = inventory.reserve(tenant, warehouse, "00123", 7, "key-1");
        assertEquals(201, first.status());
        assertEquals(3, inventory.stock(tenant, warehouse, "00123").orElseThrow().available());
        var replay = inventory.reserve(tenant, warehouse, "00123", 7, "key-1");
        assertTrue(replay.replayed());
        assertEquals(first.body(), replay.body());
        assertEquals(1, inventory.stock(tenant, warehouse, "00123").orElseThrow().version());
        RunFailure conflict = assertThrows(RunFailure.class,
            () -> inventory.reserve(tenant, warehouse, "00123", 6, "key-1"));
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.code);
        var denied = inventory.reserve(tenant, warehouse, "00123", 4, "key-2");
        assertEquals(409, denied.status());
        assertEquals(denied.body(), inventory.reserve(tenant, warehouse, "00123", 4, "key-2").body());
        try (var executor = Executors.newFixedThreadPool(2)) {
          var start = new CountDownLatch(1);
          var calls = new ArrayList<Future<RunInventory.OperationResponse>>();
          for (int n = 0; n < 2; n++) {
            String id = "race-" + n;
            calls.add(executor.submit(() -> {
              start.await();
              return inventory.reserve(tenant, warehouse, "00123", 3, id);
            }));
          }
          start.countDown();
          long successes = 0;
          for (var call : calls) if (call.get(5, TimeUnit.SECONDS).status() == 201) successes++;
          assertEquals(1, successes);
        }
        assertEquals(0, inventory.stock(tenant, warehouse, "00123").orElseThrow().available());
        assertEquals(2, inventory.stock(tenant, warehouse, "00123").orElseThrow().version());
        try (var check = DriverManager.getConnection(target, owner);
            var statement = check.createStatement();
            var rows = statement.executeQuery("SELECT count(*) FROM " + schema + ".reservation")) {
          rows.next();
          assertEquals(2, rows.getLong(1));
        }
      } finally {
        try (var statement = admin.createStatement()) {
          statement.execute("DROP DATABASE " + database + " WITH (FORCE)");
          statement.execute("DROP ROLE " + role);
        }
      }
    } finally {
      Files.deleteIfExists(keyFile);
    }
  }
}
