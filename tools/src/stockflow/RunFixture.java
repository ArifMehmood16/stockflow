package stockflow;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;

/** Explicit, run-owned writable stock. The diagnostic catalog is read only. */
final class RunFixture {
  record Receipt(String schema, int actualRows, String profile) {}

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Set<PosixFilePermission> PRIVATE_FILE =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
  private static final Set<PosixFilePermission> PRIVATE_DIRECTORY =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);

  static String schema(UUID runId) {
    return "sf_run_" + runId.toString().replace("-", "");
  }

  static UUID fixtureId(String seed, String kind, int index) {
    return UUID.nameUUIDFromBytes(
        ("stockflow-fixture:" + seed + ":" + kind + ":" + index)
            .getBytes(StandardCharsets.UTF_8));
  }

  private static String role(String kind, UUID runId) {
    return "sf_" + kind + "_" + runId.toString().replace("-", "");
  }

  private static String password() {
    byte[] bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static void secret(Path path, String role, String password) throws Exception {
    Path parent = path.getParent();
    Files.createDirectories(parent);
    Files.setPosixFilePermissions(parent, PRIVATE_DIRECTORY);
    if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) Files.createFile(path);
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      throw new IllegalStateException("Credential path is not a regular file.");
    Files.setPosixFilePermissions(path, PRIVATE_FILE);
    Files.writeString(
        path,
        "role=" + role + "\npassword=" + password + "\n",
        StandardCharsets.UTF_8,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  private static boolean roleExists(Connection connection, String name) throws SQLException {
    try (var query = connection.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname=?")) {
      query.setString(1, name);
      try (var rows = query.executeQuery()) {
        return rows.next();
      }
    }
  }

  private static void requireRoleCreation(Connection connection) throws SQLException {
    try (var statement = connection.createStatement();
        var rows =
            statement.executeQuery(
                "SELECT rolcreaterole OR rolsuper FROM pg_roles WHERE rolname=current_user")) {
      if (!rows.next() || !rows.getBoolean(1))
        throw new SQLException("Fixture setup requires CREATE ROLE authority.", "42501");
    }
  }

  private static long[] diskBudget(Connection connection, long estimated, boolean explicit)
      throws Exception {
    String location;
    try (var statement = connection.createStatement();
        var rows = statement.executeQuery("SHOW data_directory")) {
      rows.next();
      location = rows.getString(1);
    } catch (SQLException denied) {
      if (!"42501".equals(denied.getSQLState())) throw denied;
      if (explicit)
        throw new IllegalStateException(
            "Explicit fixture requires visibility of PostgreSQL data-directory free space.");
      return new long[] {-1, 0};
    }
    try {
      long free = Files.getFileStore(Path.of(location)).getUsableSpace();
      if (free < estimated)
        throw new IllegalStateException("Insufficient PostgreSQL disk budget for this fixture.");
      return new long[] {free, 1};
    } catch (java.nio.file.NoSuchFileException | java.nio.file.AccessDeniedException unavailable) {
      if (explicit)
        throw new IllegalStateException(
            "Explicit fixture requires visibility of PostgreSQL data-directory free space.");
      return new long[] {-1, 0};
    }
  }

  static Receipt prepare(Connection connection, UUID runId, String seed, Integer requested)
      throws Exception {
    if (runId == null || seed == null || seed.isBlank() || seed.length() > 64)
      throw new IllegalArgumentException("Run ID and a 1–64 character fixture seed are required.");
    if (requested != null && (requested < 1 || requested > 10_000_000))
      throw new IllegalArgumentException("FIXTURE_ROWS must be between 1 and 10,000,000.");
    Migrations.registry(connection);
    try (var lock = connection.prepareStatement("SELECT pg_advisory_lock(73628403, ?)");
        var unlock = connection.prepareStatement("SELECT pg_advisory_unlock(73628403, ?)")) {
      lock.setInt(1, runId.hashCode());
      unlock.setInt(1, runId.hashCode());
      lock.execute();
      try {
        return prepareLocked(connection, runId, seed, requested);
      } finally {
        unlock.execute();
      }
    }
  }

  private static Receipt prepareLocked(Connection connection, UUID runId, String seed,
      Integer requested) throws Exception {
    String schema = schema(runId);
    String profile = requested == null ? "small" : "explicit";
    try (var query =
        connection.prepareStatement(
            "SELECT seed,profile,requested_rows,actual_rows,state FROM stockflow_runs.ownership"
                + " WHERE run_id=?")) {
      query.setObject(1, runId);
      try (var rows = query.executeQuery()) {
        if (rows.next()) {
          Integer savedRequested = (Integer) rows.getObject(3);
          if (!seed.equals(rows.getString(1))
              || !profile.equals(rows.getString(2))
              || !Objects.equals(requested, savedRequested))
            throw new IllegalArgumentException("Run ID belongs to a different fixture request.");
          if ("READY".equals(rows.getString(5))) {
            int savedActual = rows.getInt(4);
            if (Database.scalar(connection, "SELECT count(*) FROM " + schema + ".inventory")
                    != savedActual)
              throw new IllegalStateException("Ready fixture row count changed; refusing reuse.");
            return new Receipt(schema, savedActual, profile);
          }
        }
      }
    }
    requireRoleCreation(connection);
    int catalogRows = Math.toIntExact(Database.scalar(connection, "SELECT count(*) FROM stockflow.catalog"));
    if (catalogRows == 0) throw new IllegalStateException("Import the catalog before a run fixture.");
    int codes = Math.min(requested == null ? 100 : requested, catalogRows);
    int actual = Math.multiplyExact(codes, requested == null ? 4 : 1);
    long estimated = actual * 2048L * 3 + 256L * 1024 * 1024;
    long[] disk = diskBudget(connection, estimated, requested != null);
    if (schemaExists(connection, schema))
      throw new IllegalStateException("Incomplete run schema exists; refusing unsafe overwrite.");
    registerPreparing(connection, runId, schema, seed, profile, requested, actual, catalogRows,
        estimated, disk);
    try {
      fill(connection, runId, schema, seed, profile, codes, actual);
      return new Receipt(schema, actual, profile);
    } catch (Exception error) {
      try (var failed =
          connection.prepareStatement(
              "UPDATE stockflow_runs.ownership SET state='FAILED' WHERE run_id=? AND"
                  + " state='PREPARING'")) {
        failed.setObject(1, runId);
        failed.executeUpdate();
      }
      throw error;
    }
  }

  private static boolean schemaExists(Connection connection, String schema) throws SQLException {
    try (var query = connection.prepareStatement("SELECT 1 FROM pg_namespace WHERE nspname=?")) {
      query.setString(1, schema);
      try (var rows = query.executeQuery()) {
        return rows.next();
      }
    }
  }

  private static void registerPreparing(
      Connection connection, UUID runId, String schema, String seed, String profile,
      Integer requested, int actual, int catalogRows, long estimated, long[] disk)
      throws SQLException {
    try (var query =
        connection.prepareStatement(
            "INSERT INTO stockflow_runs.ownership(run_id,schema_name,seed,profile,"
                + " requested_rows,actual_rows,catalog_rows,capped_by,estimated_bytes,"
                + " free_bytes_at_admission,disk_check,state)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,'PREPARING') ON CONFLICT(run_id) DO UPDATE"
                + " SET state='PREPARING',actual_rows=EXCLUDED.actual_rows,"
                + " catalog_rows=EXCLUDED.catalog_rows,estimated_bytes=EXCLUDED.estimated_bytes,"
                + " free_bytes_at_admission=EXCLUDED.free_bytes_at_admission,"
                + " disk_check=EXCLUDED.disk_check WHERE stockflow_runs.ownership.state IN"
                + " ('FAILED','PREPARING')")) {
      query.setObject(1, runId);
      query.setString(2, schema);
      query.setString(3, seed);
      query.setString(4, profile);
      if (requested == null) query.setNull(5, Types.INTEGER);
      else query.setInt(5, requested);
      query.setInt(6, actual);
      query.setInt(7, catalogRows);
      query.setString(8, requested != null && requested > catalogRows ? "catalog" : "none");
      query.setLong(9, estimated);
      if (disk[1] == 0) query.setNull(10, Types.BIGINT);
      else query.setLong(10, disk[0]);
      query.setString(11, disk[1] == 0 ? "not_visible" : "measured");
      if (query.executeUpdate() != 1)
        throw new IllegalStateException("Another fixture preparation owns this run ID.");
    }
  }

  private static void createRole(Connection connection, String role, String password)
      throws SQLException {
    if (roleExists(connection, role))
      throw new IllegalStateException("Run role already exists outside this preparation.");
    try (var statement = connection.createStatement()) {
      statement.execute("CREATE ROLE " + role + " LOGIN PASSWORD '" + password + "'");
    }
  }

  static void catalogReader(Connection connection) throws Exception {
    boolean original = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try {
      ensureCatalogReader(connection);
      connection.commit();
    } catch (Exception error) {
      connection.rollback();
      throw error;
    } finally {
      connection.setAutoCommit(original);
    }
  }

  private static void ensureCatalogReader(Connection connection) throws Exception {
    String role = "stockflow_catalog_reader";
    Path path = Path.of(".lab/catalog-reader.properties");
    boolean exists = roleExists(connection, role);
    if (!exists || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      String generated = password();
      if (exists) {
        try (var statement = connection.createStatement()) {
          statement.execute("ALTER ROLE " + role + " PASSWORD '" + generated + "'");
        }
      } else createRole(connection, role, generated);
      secret(path, role, generated);
    }
    Files.setPosixFilePermissions(path, PRIVATE_FILE);
    try (var statement = connection.createStatement()) {
      statement.execute("ALTER ROLE " + role + " SET default_transaction_read_only=on");
      statement.execute("GRANT USAGE ON SCHEMA stockflow TO " + role);
      statement.execute("GRANT SELECT ON stockflow.catalog,stockflow.inventory,"
          + "stockflow.dataset_import TO " + role);
    }
  }

  private static void fill(
      Connection connection, UUID runId, String schema, String seed, String profile,
      int codes, int actual) throws Exception {
    boolean original = connection.getAutoCommit();
    connection.setAutoCommit(false);
    Path directory = Path.of(".lab/runs", runId.toString());
    try {
      Migrations.runSchema(connection, schema);
      try (var copy =
          connection.prepareStatement(
              "INSERT INTO " + schema + ".catalog_snapshot"
                  + " (code,product_name,brands,categories,source)"
                  + " SELECT code,product_name,brands,categories,source FROM stockflow.catalog"
                  + " ORDER BY code LIMIT ?")) {
        copy.setInt(1, codes);
        if (copy.executeUpdate() != codes)
          throw new IllegalStateException("Catalog snapshot count changed during preparation.");
      }
      UUID t0 = fixtureId(seed, "tenant", 0), t1 = fixtureId(seed, "tenant", 1);
      UUID w0 = fixtureId(seed, "warehouse", 0), w1 = fixtureId(seed, "warehouse", 1);
      if ("small".equals(profile)) {
        for (UUID tenant : List.of(t0, t1))
          for (UUID warehouse : List.of(w0, w1))
            insertStock(connection, schema, tenant, warehouse);
      } else {
        try (var query =
            connection.prepareStatement(
                "INSERT INTO " + schema + ".inventory"
                    + " (tenant_id,warehouse_id,sku,available,version)"
                    + " SELECT CASE WHEN n % 2=0 THEN ?::uuid ELSE ?::uuid END,"
                    + " CASE WHEN n % 2=0 THEN ?::uuid ELSE ?::uuid END,code,1000,0 FROM"
                    + " (SELECT code,row_number() OVER(ORDER BY code)-1 AS n FROM " + schema
                    + ".catalog_snapshot) selected")) {
          query.setObject(1, t0);
          query.setObject(2, t1);
          query.setObject(3, w0);
          query.setObject(4, w1);
          query.executeUpdate();
        }
      }
      if (Database.scalar(connection, "SELECT count(*) FROM " + schema + ".inventory") != actual)
        throw new IllegalStateException("Fixture stock count mismatch; no READY receipt written.");
      String writer = role("w", runId), cleanup = role("c", runId), reader = role("r", runId);
      String writerPassword = password(), cleanupPassword = password(), readerPassword = password();
      ensureCatalogReader(connection);
      createRole(connection, writer, writerPassword);
      createRole(connection, cleanup, cleanupPassword);
      createRole(connection, reader, readerPassword);
      try (var statement = connection.createStatement()) {
        statement.execute("ALTER ROLE " + reader + " SET default_transaction_read_only=on");
        statement.execute("GRANT USAGE ON SCHEMA " + schema + " TO " + writer + "," + cleanup + "," + reader);
        statement.execute("GRANT SELECT,INSERT,UPDATE ON ALL TABLES IN SCHEMA " + schema + " TO " + writer);
        statement.execute("GRANT SELECT ON ALL TABLES IN SCHEMA " + schema + " TO " + reader);
        statement.execute("GRANT SELECT,DELETE ON " + schema + ".reservation,"
            + schema + ".idempotency_record TO " + cleanup);
      }
      secret(directory.resolve("writer.properties"), writer, writerPassword);
      secret(directory.resolve("cleanup.properties"), cleanup, cleanupPassword);
      secret(directory.resolve("reader.properties"), reader, readerPassword);
      try (var ready =
          connection.prepareStatement(
              "UPDATE stockflow_runs.ownership SET state='READY' WHERE run_id=? AND"
                  + " state='PREPARING'")) {
        ready.setObject(1, runId);
        if (ready.executeUpdate() != 1)
          throw new IllegalStateException("Fixture ownership state changed during preparation.");
      }
      connection.commit();
    } catch (Exception error) {
      connection.rollback();
      throw error;
    } finally {
      connection.setAutoCommit(original);
    }
  }

  private static void insertStock(Connection connection, String schema, UUID tenant, UUID warehouse)
      throws SQLException {
    try (var query =
        connection.prepareStatement(
            "INSERT INTO " + schema + ".inventory"
                + " (tenant_id,warehouse_id,sku,available,version)"
                + " SELECT ?::uuid,?::uuid,code,1000,0 FROM " + schema + ".catalog_snapshot")) {
      query.setObject(1, tenant);
      query.setObject(2, warehouse);
      query.executeUpdate();
    }
  }

  static void dropOwned(Connection connection, UUID runId) throws Exception {
    String schema = schema(runId);
    try (var query =
        connection.prepareStatement("SELECT schema_name FROM stockflow_runs.ownership WHERE run_id=?")) {
      query.setObject(1, runId);
      try (var rows = query.executeQuery()) {
        if (!rows.next() || !schema.equals(rows.getString(1)))
          throw new IllegalArgumentException("No matching owned run schema.");
      }
    }
    boolean original = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try (var statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA " + schema + " CASCADE");
      for (String kind : List.of("w", "c", "r"))
        statement.execute("DROP ROLE IF EXISTS " + role(kind, runId));
      try (var remove = connection.prepareStatement(
          "DELETE FROM stockflow_runs.migration_history WHERE scope=?")) {
        remove.setString(1, schema);
        remove.executeUpdate();
      }
      try (var remove = connection.prepareStatement(
          "DELETE FROM stockflow_runs.ownership WHERE run_id=?")) {
        remove.setObject(1, runId);
        remove.executeUpdate();
      }
      connection.commit();
    } catch (Exception error) {
      connection.rollback();
      throw error;
    } finally {
      connection.setAutoCommit(original);
    }
    Path directory = Path.of(".lab/runs", runId.toString());
    for (String kind : List.of("writer", "cleanup", "reader"))
      Files.deleteIfExists(directory.resolve(kind + ".properties"));
    Files.deleteIfExists(directory.resolve("credential.key"));
    Files.deleteIfExists(directory);
  }
}
