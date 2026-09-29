package stockflow;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class Api {
  static int port() {
    int value = Integer.parseInt(System.getenv().getOrDefault("API_PORT", "8081"));
    if (value < 1024 || value > 65535)
      throw new IllegalArgumentException("API_PORT must be 1024..65535");
    return value;
  }

  static Path registry() {
    return Path.of(".lab/api");
  }

  static Path snapshot(Path source, Path directory) throws Exception {
    Files.createDirectories(directory);
    Path target = Files.createTempFile(directory, "runtime-", ".jar");
    try {
      return Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (java.io.IOException error) {
      Files.deleteIfExists(target);
      throw error;
    }
  }

  static void removeSnapshot(Path snapshot) {
    try {
      Files.deleteIfExists(snapshot);
    } catch (java.io.IOException ignored) {
      /* Retain a failed cleanup in ignored .lab; never delete other files. */
    }
  }

  static void configure(ProcessBuilder builder) throws Exception {
    var settings = Lab.settings();
    var config = Database.config(settings.getOrDefault("DATABASE_URL", ""), false);
    builder.environment().put("STOCKFLOW_JDBC_URL", config.url());
    builder.environment().put("STOCKFLOW_JDBC_USER", config.properties().getProperty("user"));
    builder
        .environment()
        .put("STOCKFLOW_JDBC_PASSWORD", config.properties().getProperty("password", ""));
    // Native launch never inherits a wildcard bind from container configuration.
    builder.environment().put("STOCKFLOW_API_BIND", "127.0.0.1");
  }

  static String java() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toString();
  }

  static Process start() throws Exception {
    int port = port();
    // Fail early without starting another JVM or disturbing the existing listener.
    try (var probe = new ServerSocket()) {
      probe.bind(new InetSocketAddress("127.0.0.1", port));
    } catch (BindException error) {
      throw new IllegalStateException(
          "API port "
              + port
              + " is already in use. Stop the owned API or choose API_PORT; no listener was"
              + " stopped.");
    }
    Path jar = Path.of("services/inventory-service/target/inventory-service-0.1.0-SNAPSHOT.jar");
    if (!Files.exists(jar))
      throw new IllegalStateException("Build the API first with make api-build.");
    var builder = new ProcessBuilder().inheritIO();
    configure(builder);
    Path runtimeJar = snapshot(jar, registry());
    builder.command(java(), "-Xmx256m", "-jar", runtimeJar.toString());
    Process process;
    try {
      process = builder.start();
    } catch (java.io.IOException error) {
      removeSnapshot(runtimeJar);
      throw error;
    }
    process.onExit().thenRun(() -> removeSnapshot(runtimeJar));
    try {
      Preview.register(registry(), port, process.toHandle());
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    process.destroy();
                    try {
                      process.waitFor(7, TimeUnit.SECONDS);
                    } catch (InterruptedException error) {
                      Thread.currentThread().interrupt();
                    }
                    if (!process.isAlive()) removeSnapshot(runtimeJar);
                  }));
      long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
      try (var client = HttpClient.newHttpClient()) {
        while (process.isAlive() && System.nanoTime() < deadline) {
          try {
            var response =
                client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health/live"))
                        .timeout(Duration.ofSeconds(1))
                        .build(),
                    HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) {
              System.out.println(
                  "StockFlow inventory API: http://127.0.0.1:" + port + " (real PostgreSQL reads)");
              return process;
            }
          } catch (java.io.IOException ignored) {
            /* Wait for this child's listener. */
          }
          Thread.sleep(100);
        }
      }
      throw new IllegalStateException(
          "Inventory API did not become live; check the startup output.");
    } catch (Exception error) {
      process.destroy();
      process.waitFor(6, TimeUnit.SECONDS);
      throw error;
    }
  }

  static String stop() throws Exception {
    return Preview.stop(registry(), port()).replace("preview", "inventory API");
  }

  static void integration() throws Exception {
    var builder =
        new ProcessBuilder(java(), "tools/MavenBuild.java", "-q", "-Pintegration", "verify")
            .inheritIO();
    configure(builder);
    if (builder.start().waitFor() != 0)
      throw new IllegalStateException("API integration checks failed.");
  }

  static void smoke() throws Exception {
    var config = Database.config(Lab.settings().getOrDefault("DATABASE_URL", ""), false);
    try (var connection = Database.connect(config);
        var query =
            connection.prepareStatement(
                "SELECT code,on_hand,version FROM stockflow.inventory LIMIT 1");
        var client = HttpClient.newHttpClient()) {
      query.setQueryTimeout(2);
      try (var row = query.executeQuery()) {
        if (!row.next()) throw new IllegalStateException("No imported inventory is available.");
        String base = "http://127.0.0.1:" + port();
        for (String path :
            List.of(
                "/health/live", "/health/ready", "/v1/catalog/" + row.getString(1) + "/stock")) {
          var response =
              client.send(
                  HttpRequest.newBuilder(URI.create(base + path))
                      .timeout(Duration.ofSeconds(6))
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
          if (response.statusCode() != 200)
            throw new IllegalStateException(
                "API smoke failed: " + path + " returned " + response.statusCode());
          if (path.startsWith("/v1/")
              && (!response.body().contains("\"code\":\"" + row.getString(1) + "\"")
                  || !response.body().contains("\"available\":" + row.getInt(2) + ",")
                  || !response.body().contains("\"version\":" + row.getLong(3) + ",")
                  || !response.body().contains("\"source\":\"postgresql-primary\"")))
            throw new IllegalStateException(
                "API response did not match the current database snapshot.");
        }
        System.out.println(
            "PASS: live/ready health and real stock endpoint match the loaded database; no writes"
                + " performed.");
      }
    }
  }
}
