package stockflow;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.postgresql.PGConnection;

final class Database {
  record Config(String url, Properties properties) {
    @Override
    public String toString() {
      return "Local PostgreSQL configuration (credentials redacted)";
    }
  }

  static String decode(String value) {
    return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
  }

  static Config config(String text, boolean docker) {
    try {
      URI uri = URI.create(text);
      var hosts =
          docker
              ? Set.of("localhost", "127.0.0.1", "[::1]", "database")
              : Set.of("localhost", "127.0.0.1", "[::1]");
      String name = uri.getRawPath() == null ? "" : decode(uri.getRawPath()).replaceFirst("^/", "");
      if (!("postgresql".equals(uri.getScheme()) || "postgresql+psycopg".equals(uri.getScheme()))
          || uri.getHost() == null
          || !hosts.contains(uri.getHost())
          || name.isBlank()
          || name.contains("/")
          || name.chars().anyMatch(c -> c < 32)
          || uri.getQuery() != null
          || uri.getFragment() != null) throw new IllegalArgumentException();
      String[] user = Optional.ofNullable(uri.getRawUserInfo()).orElse("postgres").split(":", 2);
      var properties = new Properties();
      properties.setProperty("user", decode(user[0]));
      if (user.length == 2) properties.setProperty("password", decode(user[1]));
      properties.setProperty("connectTimeout", "5");
      properties.setProperty("socketTimeout", "900");
      properties.setProperty("ApplicationName", "stockflow-bootstrap");
      // Re-encode the database segment; never append caller-supplied JDBC options.
      String path = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
      return new Config(
          "jdbc:postgresql://"
              + uri.getHost()
              + ":"
              + (uri.getPort() == -1 ? 5432 : uri.getPort())
              + "/"
              + path,
          properties);
    } catch (IllegalArgumentException error) {
      throw new IllegalArgumentException(
          "Set DATABASE_URL to an existing local PostgreSQL database, without query options."
              + " Credentials are not printed.");
    }
  }

  static Connection connect(Config config) throws SQLException {
    return DriverManager.getConnection(config.url(), config.properties());
  }

  static void initialize(Connection connection) throws Exception {
    try (var statement = connection.createStatement()) {
      statement.execute(Files.readString(Path.of("infra/schema.sql")));
    }
    System.out.println("StockFlow schema ready; other schemas untouched.");
  }

  static long scalar(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement();
        var result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  static boolean isLoaded(Connection connection, int rows, String checksum) throws SQLException {
    try (var query =
        connection.prepareStatement(
            "SELECT selected_rows FROM stockflow.dataset_import WHERE source_url=? AND"
                + " (selected_rows>=? OR source_complete) AND (?::text IS NULL OR subset_sha256=?)"
                + " ORDER BY selected_rows DESC")) {
      query.setString(1, Dataset.SOURCE);
      query.setInt(2, rows);
      query.setString(3, checksum);
      query.setString(4, checksum);
      try (var result = query.executeQuery()) {
        if (!result.next()) return false;
        long expected = result.getLong(1);
        return scalar(connection, "SELECT count(*) FROM stockflow.catalog") >= expected
            && scalar(connection, "SELECT count(*) FROM stockflow.inventory") >= expected;
      }
    }
  }

  static void importDataset(Connection connection, Dataset.Subset subset) throws Exception {
    if (!Dataset.checksum(subset.path()).equals(subset.sha256()))
      throw new IllegalArgumentException("Dataset checksum mismatch; no import performed.");
    if (subset.rows() < 1 || subset.rows() > 10000000)
      throw new IllegalArgumentException("Invalid dataset row count.");
    connection.setAutoCommit(false);
    try (var statement = connection.createStatement()) {
      statement.execute(
          "SET LOCAL statement_timeout='15min'; SET LOCAL lock_timeout='15s'; SELECT"
              + " pg_advisory_xact_lock(73628401)");
      if (isLoaded(connection, subset.rows(), subset.sha256())) {
        connection.commit();
        System.out.println("Dataset already loaded; skipping COPY and inventory writes.");
        return;
      }
      statement.execute(
          "CREATE TEMP TABLE seed (code text PRIMARY KEY, product_name text, brands text,"
              + " categories text, tenant_id integer, bucket smallint, on_hand integer) ON COMMIT"
              + " DROP");
      try (var source = Files.newInputStream(subset.path())) {
        long copied =
            connection
                .unwrap(PGConnection.class)
                .getCopyAPI()
                .copyIn(
                    "COPY seed FROM STDIN WITH (FORMAT csv, FORCE_NOT_NULL"
                        + " (product_name,brands,categories))",
                    source);
        if (copied != subset.rows())
          throw new IllegalStateException("Selected row count mismatch; import rolled back.");
      }
      statement.executeUpdate(
          "INSERT INTO stockflow.catalog (code,product_name,brands,categories) SELECT"
              + " code,product_name,brands,categories FROM seed WHERE true ON CONFLICT (code) DO"
              + " NOTHING");
      statement.executeUpdate(
          "INSERT INTO stockflow.inventory (code,tenant_id,bucket,on_hand) SELECT"
              + " code,tenant_id,bucket,on_hand FROM seed WHERE true ON CONFLICT (code) DO"
              + " NOTHING");
      if (scalar(connection, "SELECT count(*) FROM seed JOIN stockflow.inventory USING (code)")
          != subset.rows())
        throw new IllegalStateException("Inventory verification failed; import rolled back.");
      try (var insert =
          connection.prepareStatement(
              "INSERT INTO stockflow.dataset_import"
                  + " (subset_sha256,source_url,selected_rows,source_complete) VALUES (?,?,?,?) ON"
                  + " CONFLICT DO NOTHING")) {
        insert.setString(1, subset.sha256());
        insert.setString(2, Dataset.SOURCE);
        insert.setInt(3, subset.rows());
        insert.setBoolean(4, subset.complete());
        insert.executeUpdate();
      }
      connection.commit();
    } catch (Exception error) {
      connection.rollback();
      throw error;
    } finally {
      connection.setAutoCommit(true);
    }
    try (var statement = connection.createStatement()) {
      statement.execute("ANALYZE stockflow.catalog; ANALYZE stockflow.inventory");
    }
    System.out.printf(
        "Verified %,d selected products in stockflow.inventory. Existing quantities preserved.%n",
        subset.rows());
    status(connection);
  }

  static void status(Connection connection) throws SQLException {
    System.out.printf(
        "Catalog rows: %,d%nInventory rows: %,d%n",
        scalar(connection, "SELECT count(*) FROM stockflow.catalog"),
        scalar(connection, "SELECT count(*) FROM stockflow.inventory"));
    try (var statement = connection.createStatement();
        var rows =
            statement.executeQuery(
                "SELECT bucket,count(*) FROM stockflow.inventory GROUP BY bucket ORDER BY"
                    + " bucket")) {
      while (rows.next())
        System.out.printf("Bucket %02d: %,d rows%n", rows.getInt(1), rows.getLong(2));
    }
  }
}
