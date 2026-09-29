package dev.stockflow.inventory;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.*;
import dev.stockflow.inventory.adapter.JdbcStockRepository;
import dev.stockflow.inventory.application.StockUnavailable;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Explicit integration profile; owns and drops only its freshly-created UUID database. */
class JdbcStockRepositoryIT {
  @Test
  void readsRealRowsAndChecksReadinessWithoutMutatingInventory() throws Exception {
    String url =
        Objects.requireNonNull(
            System.getenv("STOCKFLOW_JDBC_URL"),
            "Use make test-api-integration to supply local configuration safely.");
    var credentials = new Properties();
    credentials.setProperty("user", System.getenv("STOCKFLOW_JDBC_USER"));
    credentials.setProperty("password", System.getenv("STOCKFLOW_JDBC_PASSWORD"));
    credentials.setProperty("connectTimeout", "2");
    String name = "stockflow_test_" + UUID.randomUUID().toString().replace("-", "");
    try (var admin = DriverManager.getConnection(url, credentials)) {
      try (var statement = admin.createStatement()) {
        statement.execute("CREATE DATABASE " + name);
      }
      try {
        String target = url.substring(0, url.lastIndexOf('/') + 1) + name;
        try (var fixture = DriverManager.getConnection(target, credentials)) {
          try (var statement = fixture.createStatement()) {
            statement.execute(Files.readString(Path.of("../../infra/schema.sql")));
            statement.execute(
                "INSERT INTO stockflow.catalog(code,product_name,brands,categories) VALUES"
                    + " ('001234','Soup <test>','',''); INSERT INTO"
                    + " stockflow.inventory(code,tenant_id,bucket,on_hand,version) VALUES"
                    + " ('001234',12,12,17,3)");
          }
          var config = new HikariConfig();
          config.setJdbcUrl(target);
          config.setUsername(credentials.getProperty("user"));
          config.setPassword(credentials.getProperty("password"));
          config.setMaximumPoolSize(2);
          config.setReadOnly(true);
          try (var source = new HikariDataSource(config)) {
            var repository = new JdbcStockRepository(source);
            assertTrue(repository.ready());
            var stock = repository.find("001234").orElseThrow();
            assertEquals(17, stock.available());
            assertEquals(3, stock.version());
            assertEquals("Soup <test>", stock.productName());
            assertEquals(12, stock.tenantId());
            assertEquals(12, stock.bucket());
            assertTrue(repository.find("999").isEmpty());
            assertTrue(
                repository.find("' OR '1'='1").isEmpty(), "SQL value must stay a bound parameter");
            try (var statement = fixture.createStatement()) {
              statement.execute(
                  "ALTER TABLE stockflow.inventory RENAME COLUMN on_hand TO unavailable_column");
            }
            assertFalse(repository.ready(), "Schema incompatibility must fail readiness");
            assertThrows(StockUnavailable.class, () -> repository.find("001234"));
            try (var statement = fixture.createStatement();
                var row =
                    statement.executeQuery(
                        "SELECT unavailable_column, version FROM stockflow.inventory WHERE"
                            + " code='001234'")) {
              assertTrue(row.next());
              assertEquals(17, row.getInt(1));
              assertEquals(3, row.getLong(2));
            }
          }
        }
      } finally {
        try (var statement = admin.createStatement()) {
          statement.execute("DROP DATABASE " + name);
        }
        System.out.println("Removed owned API integration database.");
      }
    }
  }
}
