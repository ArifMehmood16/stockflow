package stockflow;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

final class IntegrationTest {
  static void run() throws Exception {
    var config = Database.config(Lab.settings().getOrDefault("DATABASE_URL", ""), false);
    String name = "stockflow_test_" + UUID.randomUUID().toString().replace("-", "");
    try (var admin = Database.connect(config)) {
      try (var statement = admin.createStatement()) {
        statement.execute("CREATE DATABASE " + name);
      }
      try {
        String target = config.url().substring(0, config.url().lastIndexOf('/') + 1) + name;
        try (var connection = Database.connect(new Database.Config(target, config.properties()))) {
          try (var statement = connection.createStatement()) {
            statement.execute(
                "CREATE TABLE public.unrelated (value integer); INSERT INTO public.unrelated VALUES"
                    + " (7)");
          }
          Database.initialize(connection);
          Database.initialize(connection);
          Path csv = Files.createTempFile("stockflow-fixture-", ".csv");
          try {
            var projected = new java.io.StringWriter();
            Dataset.projectRows(
                new java.io.StringReader(
                    "code\tproduct_name\tbrands\tcategories\n"
                        + "123\tFresh 6\" item\t\tMeals\n"
                        + "456\tRice\tBrand\tGrain\n"),
                projected,
                2);
            Files.writeString(csv, projected.toString());
            var subset = new Dataset.Subset(csv, 2, Dataset.checksum(csv), true);
            Database.importDataset(connection, subset);
            ToolTests.check(
                Database.isLoaded(connection, 2, subset.sha256()),
                "Already imported fixture must be detected without another COPY");
            ToolTests.check(
                Database.isLoaded(connection, 10000000, subset.sha256()),
                "Complete smaller source must satisfy a larger requested maximum");
            try (var statement = connection.createStatement()) {
              statement.execute("UPDATE stockflow.inventory SET on_hand=7 WHERE code='123'");
            }
            Database.importDataset(connection, subset);
            ToolTests.check(
                Database.scalar(connection, "SELECT count(*) FROM stockflow.catalog") == 2,
                "Import remains idempotent");
            ToolTests.check(
                Database.scalar(
                        connection, "SELECT on_hand FROM stockflow.inventory WHERE code='123'")
                    == 7,
                "Existing inventory preserved");
            ToolTests.check(
                Database.scalar(connection, "SELECT value FROM public.unrelated") == 7,
                "Unrelated schema untouched");
            try (var statement = connection.createStatement()) {
              statement.execute("DELETE FROM stockflow.inventory WHERE code='456'");
            }
            ToolTests.check(
                !Database.isLoaded(connection, 2, subset.sha256()),
                "Missing rows invalidate the skip check");
            Database.importDataset(connection, subset);
            ToolTests.check(
                Database.scalar(connection, "SELECT count(*) FROM stockflow.inventory") == 2,
                "Missing inventory is repaired without resetting existing quantities");
            Files.writeString(csv, "789,Invalid,,,1,1,-1\n");
            boolean rejected = false;
            try {
              Database.importDataset(connection, new Dataset.Subset(csv, 1, Dataset.checksum(csv)));
            } catch (SQLException expected) {
              rejected = true;
            }
            ToolTests.check(rejected, "Negative stock rejects transaction");
            ToolTests.check(
                Database.scalar(
                        connection, "SELECT count(*) FROM stockflow.catalog WHERE code='789'")
                    == 0,
                "No partial catalog insert survives rollback");
            System.out.println(
                "PASS: real JDBC COPY, empty text fields, schema idempotence, stock preservation,"
                    + " isolation and atomic rollback.");
          } finally {
            Files.deleteIfExists(csv);
          }
        }
      } finally {
        try (var statement = admin.createStatement()) {
          statement.execute("DROP DATABASE " + name);
        }
        System.out.println("Removed the owned temporary integration database.");
      }
    }
  }
}
