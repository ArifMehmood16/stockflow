package stockflow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HexFormat;

final class Migrations {
  private Migrations() {}

  static void registry(Connection connection) throws Exception {
    boolean original = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try (var statement = connection.createStatement()) {
      statement.execute("SELECT pg_advisory_xact_lock(73628402)");
      statement.execute("CREATE SCHEMA IF NOT EXISTS stockflow_runs");
      statement.execute(
          "CREATE TABLE IF NOT EXISTS stockflow_runs.migration_history (scope text NOT NULL,"
              + " version integer NOT NULL, checksum text NOT NULL, applied_at timestamptz NOT"
              + " NULL DEFAULT now(), PRIMARY KEY(scope,version))");
      apply(connection, "registry", 1, Path.of("infra/migrations/001-run-registry.sql"));
      connection.commit();
    } catch (Exception error) {
      connection.rollback();
      throw error;
    } finally {
      connection.setAutoCommit(original);
    }
  }

  static void runSchema(Connection connection, String schema) throws Exception {
    if (!schema.matches("sf_run_[0-9a-f]{32}"))
      throw new IllegalArgumentException("Invalid run schema name.");
    try (var statement = connection.createStatement()) {
      statement.execute("CREATE SCHEMA " + schema);
      statement.execute("SELECT set_config('search_path', '" + schema + ",pg_catalog', true)");
    }
    apply(connection, schema, 2, Path.of("infra/migrations/002-run-schema.sql"));
  }

  static void apply(Connection connection, String scope, int version, Path file) throws Exception {
    byte[] source = Files.readAllBytes(file);
    String checksum =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
    try (var query =
        connection.prepareStatement(
            "SELECT checksum FROM stockflow_runs.migration_history WHERE scope=? AND"
                + " version=?")) {
      query.setString(1, scope);
      query.setInt(2, version);
      try (var rows = query.executeQuery()) {
        if (rows.next()) {
          if (!checksum.equals(rows.getString(1)))
            throw new IllegalStateException("Migration checksum mismatch for " + scope + ".");
          return;
        }
      }
    }
    try (var statement = connection.createStatement()) {
      statement.execute(new String(source, java.nio.charset.StandardCharsets.UTF_8));
    }
    try (var insert =
        connection.prepareStatement(
            "INSERT INTO stockflow_runs.migration_history(scope,version,checksum)"
                + " VALUES (?,?,?)")) {
      insert.setString(1, scope);
      insert.setInt(2, version);
      insert.setString(3, checksum);
      insert.executeUpdate();
    }
  }
}
