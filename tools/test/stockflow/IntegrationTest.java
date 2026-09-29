package stockflow;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

final class IntegrationTest {
  static void run() throws Exception {
    var config = Database.config(Lab.settings().getOrDefault("DATABASE_URL", ""), false);
    String name = "stockflow_test_" + UUID.randomUUID().toString().replace("-", "");
    try (var admin = Database.connect(config)) {
      boolean catalogRoleExisted =
          Database.scalar(admin, "SELECT count(*) FROM pg_roles WHERE rolname='stockflow_catalog_reader'")
              > 0;
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
          Migrations.registry(connection);
          Migrations.registry(connection);
          ToolTests.check(
              Database.scalar(connection, "SELECT count(*) FROM stockflow_runs.ownership") == 0,
              "Run registry migration creates an empty ownership table");
          Path changedMigration = Files.createTempFile("stockflow-migration-mismatch-", ".sql");
          try {
            Files.writeString(changedMigration, "SELECT 1;");
            boolean rejected = false;
            try {
              Migrations.apply(connection, "registry", 1, changedMigration);
            } catch (IllegalStateException expected) {
              rejected = true;
            }
            ToolTests.check(rejected, "Changed migration checksum is rejected");
          } finally {
            Files.deleteIfExists(changedMigration);
          }
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
            UUID runId = UUID.randomUUID();
            String writer = "sf_w_" + runId.toString().replace("-", "");
            try (var statement = connection.createStatement()) {
              statement.execute("CREATE ROLE " + writer);
            }
            boolean incompleteRejected = false;
            try {
              RunFixture.prepare(connection, runId, "fixture-test", null);
            } catch (IllegalStateException expected) {
              incompleteRejected = true;
            }
            ToolTests.check(incompleteRejected, "A conflicting role aborts fixture preparation");
            ToolTests.check(
                Database.scalar(
                        connection,
                        "SELECT count(*) FROM stockflow_runs.ownership WHERE run_id='" + runId
                            + "' AND state='READY'")
                    == 0,
                "Failed preparation never publishes READY");
            ToolTests.check(
                Database.scalar(
                        connection,
                        "SELECT count(*) FROM pg_namespace WHERE nspname='" + RunFixture.schema(runId)
                            + "'")
                    == 0,
                "Failed preparation rolls back its run schema");
            try (var statement = connection.createStatement()) {
              statement.execute("DROP ROLE " + writer);
            }
            var fixture = RunFixture.prepare(connection, runId, "fixture-test", null);
            ToolTests.check(fixture.actualRows() == 8, "Small fixture uses available catalog codes");
            ToolTests.check(
                Database.scalar(connection, "SELECT count(*) FROM " + fixture.schema() + ".inventory")
                    == 8,
                "Two tenants and two warehouses receive each small-profile SKU");
            try (var statement = connection.createStatement()) {
              statement.execute("UPDATE " + fixture.schema() + ".inventory SET available=999");
            }
            RunFixture.prepare(connection, runId, "fixture-test", null);
            ToolTests.check(
                Database.scalar(
                        connection,
                        "SELECT count(*) FROM " + fixture.schema() + ".inventory WHERE available=999")
                    == 8,
                "Verified repeat fixture leaves writable quantities untouched");
            ToolTests.check(
                Database.scalar(connection, "SELECT value FROM public.unrelated") == 7,
                "Run fixture leaves unrelated sentinel table untouched");
            boolean unownedRejected = false;
            try {
              RunFixture.dropOwned(connection, UUID.randomUUID());
            } catch (IllegalArgumentException expected) {
              unownedRejected = true;
            }
            ToolTests.check(unownedRejected, "Cleanup rejects an unowned run");
            ToolTests.check(
                Database.scalar(
                        connection,
                        "SELECT CASE WHEN has_table_privilege('" + writer
                            + "','stockflow.inventory','UPDATE') THEN 1 ELSE 0 END")
                    == 0,
                "Run writer cannot update diagnostic inventory");
            ToolTests.check(
                Database.scalar(
                        connection,
                        "SELECT CASE WHEN has_table_privilege('" + writer + "','" + fixture.schema()
                            + ".inventory','DELETE') THEN 1 ELSE 0 END")
                    == 0,
                "Run writer cannot delete stock rows");
            UUID explicitRun = UUID.randomUUID();
            var explicit = RunFixture.prepare(connection, explicitRun, "fixture-test", 3);
            ToolTests.check(explicit.actualRows() == 2, "Explicit fixture caps at real catalog size");
            RunFixture.dropOwned(connection, explicitRun);
            RunFixture.dropOwned(connection, runId);
            ToolTests.check(
                Database.scalar(connection, "SELECT value FROM public.unrelated") == 7,
                "Owned cleanup leaves unrelated sentinel table untouched");
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
          if (!catalogRoleExisted) statement.execute("DROP ROLE IF EXISTS stockflow_catalog_reader");
        }
        if (!catalogRoleExisted) Files.deleteIfExists(Path.of(".lab/catalog-reader.properties"));
        System.out.println("Removed the owned temporary integration database.");
      }
    }
  }
}
