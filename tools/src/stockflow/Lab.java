package stockflow;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

public final class Lab {
  static Map<String, String> settings() throws IOException {
    var result = new HashMap<>(System.getenv());
    Path file = Path.of(".env");
    if (Files.exists(file))
      for (String line : Files.readAllLines(file)) {
        if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
        String[] entry = line.split("=", 2);
        if (entry.length == 2
            && Set.of("DATABASE_URL", "DATASET_ROWS", "FIXTURE_ROWS", "FIXTURE_SEED")
                .contains(entry[0].trim())) {
          String value = entry[1].trim();
          if (value.length() > 1
              && ((value.startsWith("\"") && value.endsWith("\""))
                  || (value.startsWith("'") && value.endsWith("'"))))
            value = value.substring(1, value.length() - 1);
          result.putIfAbsent(entry[0].trim(), value);
        }
      }
    return result;
  }

  static void setup(String action) throws Exception {
    var settings = settings();
    int rows = Integer.parseInt(settings.getOrDefault("DATASET_ROWS", "10000000"));
    if (rows < 1 || rows > 10000000)
      throw new IllegalArgumentException("DATASET_ROWS must be 1..10,000,000.");
    Files.createDirectories(Dataset.CACHE);
    try (var channel =
            FileChannel.open(
                Dataset.CACHE.resolve("setup.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        var lock = channel.tryLock()) {
      if (lock == null)
        throw new IllegalStateException(
            "Another StockFlow setup is using this dataset cache. Wait for it to finish.");
      if (action.equals("data-fetch")) {
        Dataset.fetch(rows);
        return;
      }
      var config =
          Database.config(
              settings.getOrDefault("DATABASE_URL", ""),
              "1".equals(settings.get("STOCKFLOW_DOCKER")));
      if ("1".equals(settings.get("STOCKFLOW_DOCKER")))
        config
            .properties()
            .setProperty("password", settings.getOrDefault("STOCKFLOW_DB_PASSWORD", ""));
      try (var connection = Database.connect(config)) {
        if (action.equals("db-status")) {
          Database.status(connection);
          return;
        }
        Database.initialize(connection);
        if (!action.equals("db-init")) {
          if (Database.isLoaded(connection, rows, null)) {
            System.out.println(
                "Matching catalog already loaded; skipping download, COPY and inventory writes.");
            Database.status(connection);
          } else Database.importDataset(connection, Dataset.fetch(rows));
        }
      }
    }
  }

  static void fixture() throws Exception {
    var settings = settings();
    var config = Database.config(settings.getOrDefault("DATABASE_URL", ""), false);
    UUID runId =
        settings.containsKey("RUN_ID")
            ? UUID.fromString(settings.get("RUN_ID"))
            : UUID.randomUUID();
    Integer requested =
        settings.containsKey("FIXTURE_ROWS")
            ? Integer.valueOf(settings.get("FIXTURE_ROWS"))
            : null;
    try (var connection = Database.connect(config)) {
      var receipt =
          RunFixture.prepare(
              connection, runId, settings.getOrDefault("FIXTURE_SEED", "stockflow-demo"),
              requested);
      System.out.printf(
          "Run %s READY; profile=%s, actual stock rows=%,d. Credentials: .lab/runs/%s/%n",
          runId, receipt.profile(), receipt.actualRows(), runId);
    }
  }

  static void docs() throws IOException {
    int checked = 0;
    var pattern = Pattern.compile("\\[[^\\]]*\\]\\(([^)]+)\\)");
    try (var files = Files.walk(Path.of("."))) {
      for (Path file :
          files
              .filter(
                  p ->
                      p.toString().endsWith(".md")
                          && !p.toString().contains("/.lab/")
                          && !p.toString().contains("/node_modules/")
                          && !p.toString().contains("/.git/"))
              .toList()) {
        var matches = pattern.matcher(Files.readString(file));
        while (matches.find()) {
          String target = matches.group(1).split("#", 2)[0];
          if (target.isBlank() || target.matches("^(https?:|mailto:).*$")) continue;
          if (!Files.exists(
              file.getParent()
                  .resolve(
                      java.net.URLDecoder.decode(target, java.nio.charset.StandardCharsets.UTF_8))))
            throw new IOException("Missing local link in " + file + ": " + target);
          checked++;
        }
      }
    }
    System.out.println("Checked " + checked + " local documentation links.");
  }

  public static void main(String[] args) {
    String action = args.length == 0 ? "help" : args[0];
    try {
      switch (action) {
        case "setup", "db-init", "db-import", "db-status", "data-fetch" -> setup(action);
        case "fixture" -> fixture();
        case "preview" -> Preview.start(Path.of("."), Preview.port());
        case "run" -> {
          Api.start();
          Preview.start(Path.of("."), Preview.port());
        }
        case "api" -> System.exit(Api.start().waitFor());
        case "stop" -> {
          System.out.println(Preview.stop(Path.of(".lab"), Preview.port()));
          System.out.println(Api.stop());
        }
        case "api-stop" -> System.out.println(Api.stop());
        case "api-smoke" -> Api.smoke();
        case "test-api-integration" -> Api.integration();
        case "check-docs" -> docs();
        case "health", "health-api" -> {
          String target =
              action.equals("health")
                  ? "http://127.0.0.1:" + Preview.port() + "/"
                  : "http://127.0.0.1:" + Api.port() + "/health/ready";
          try (var client = java.net.http.HttpClient.newHttpClient()) {
            var response =
                client.send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create(target))
                        .timeout(java.time.Duration.ofSeconds(5))
                        .build(),
                    java.net.http.HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 200) System.exit(1);
          }
        }
        case "doctor" -> {
          System.out.println("Java: " + Runtime.version());
          System.out.println("JAVA_HOME: " + System.getProperty("java.home"));
          System.out.println(
              "Local PostgreSQL: configure existing database in .env; use make db-status to"
                  + " verify.");
          System.out.println("Docker: optional, requires a running Docker Engine and Compose v2.");
        }
        case "test-postgres" -> IntegrationTest.run();
        default ->
            System.out.println(
                "StockFlow Java tools: setup | fixture | db-init | db-import | db-status | data-fetch |"
                    + " preview | stop | doctor");
      }
    } catch (SQLException error) {
      System.err.println(
          "StockFlow database operation failed (SQLSTATE "
              + error.getSQLState()
              + "). Check database name, permissions and schema compatibility. Import transactions"
              + " roll back on failure; credentials are not printed.");
      if (error instanceof org.postgresql.util.PSQLException pg
          && pg.getServerErrorMessage() != null) {
        String where = Optional.ofNullable(pg.getServerErrorMessage().getWhere()).orElse("");
        var line = Pattern.compile("line (\\d+)").matcher(where);
        if (line.find()) System.err.println("COPY source row: " + line.group(1));
        String message = Optional.ofNullable(pg.getServerErrorMessage().getMessage()).orElse("");
        for (String known :
            List.of(
                "extra data after last expected column",
                "unterminated CSV quoted field",
                "unquoted carriage return",
                "unquoted newline",
                "missing data for column"))
          if (message.contains(known)) System.err.println("COPY format: " + known);
      }
      System.exit(1);
    } catch (IllegalArgumentException | IllegalStateException | IOException error) {
      System.err.println("StockFlow: " + error.getMessage());
      System.exit(1);
    } catch (Exception error) {
      System.err.println(
          "StockFlow operation failed: "
              + error.getClass().getSimpleName()
              + ". No credentials or source payload printed.");
      System.exit(1);
    }
  }
}
