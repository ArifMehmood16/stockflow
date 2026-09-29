package stockflow;

import java.util.*;

/** Offline behaviour checks, including loopback HTTP and owned child processes. */
public final class ToolTests {
  static int passed;

  static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
    passed++;
  }

  static void rejects(Runnable operation, String message) {
    try {
      operation.run();
    } catch (IllegalArgumentException expected) {
      passed++;
      return;
    }
    throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    var configFile = java.nio.file.Files.createTempFile("stockflow-settings", ".env");
    try {
      java.nio.file.Files.writeString(configFile, "RUN_ID='4646aad0-3c39-499c-92c8-7ca4cd96ca81'\n");
      check(Lab.settings(configFile, Map.of()).get("RUN_ID").equals("4646aad0-3c39-499c-92c8-7ca4cd96ca81"),
          "Saved run selection enables real mode without shell exports");
      check(Lab.settings(configFile, Map.of("RUN_ID", "override")).get("RUN_ID").equals("override"),
          "Explicit environment run overrides saved selection");
    } finally { java.nio.file.Files.deleteIfExists(configFile); }
    if (args.length > 0 && args[0].equals("child")) {
      System.out.println("ready");
      Thread.sleep(60000);
      return;
    }
    var config =
        Database.config("postgresql+psycopg://postgres:p%40ss@localhost:5432/postgres", false);
    check(config.url().equals("jdbc:postgresql://localhost:5432/postgres"), "Normalize driver URL");
    check(
        config.properties().getProperty("password").equals("p@ss"),
        "Decode credentials separately");
    check(
        !config.url().contains("p@ss") && !config.toString().contains("p@ss"),
        "Do not display credentials");
    for (String value :
        List.of(
            "https://localhost/db",
            "postgresql://outside.test/db",
            "postgresql://localhost/",
            "postgresql://localhost/a/b",
            "postgresql://localhost/db?options=unsafe"))
      rejects(() -> Database.config(value, false), "Reject remote or malformed database URL");
    String[] row = {"123", "Soup\n\\.\r\u0000" + "x".repeat(2000), "Brand", "Meals"};
    String[] product = Dataset.project(row);
    check(
        product[1].length() == 512 && !product[1].contains("\n") && !product[1].contains("\u0000"),
        "Bound and clean text");
    check(
        Integer.parseInt(product[5]) < 16 && Integer.parseInt(product[6]) >= 0,
        "Bound synthetic data");
    check(Arrays.equals(product, Dataset.project(row)), "Deterministic fixture");
    check(Dataset.project(new String[] {"bad", "Bad", "", ""}) == null, "Reject invalid codes");
    String input =
        "code\tproduct_name\tbrands\tcategories\n"
            + "123\tSoup\t\tMeals\n"
            + "123\tDuplicate\t\tMeals\n"
            + "bad\tBad\t\tOther\n"
            + "456\tRice\t\tGrain\n";
    var output = new java.io.StringWriter();
    check(
        Dataset.projectRows(new java.io.StringReader(input), output, 2) == 2,
        "Select exactly two unique products");
    check(output.toString().lines().count() == 2, "No duplicate output");
    check(
        Dataset.projectRows(new java.io.StringReader(input), new java.io.StringWriter(), 3) == 2,
        "Use available unique products when the source is smaller than the requested maximum");
    previewTests();
    apiPortTest();
    apiArtifactTest();
    apiReaderCredentialsTest();
    trafficTests();
    trafficTargetTest();
    trafficControlTest();
    apiPoolTest();
    System.out.println("Passed " + passed + " Java behaviour assertions.");
  }

  static void trafficTests() throws Exception {
    rejects(() -> new TrafficRun.Limits(51, 1, 1), "Reject excessive offered rate");
    rejects(() -> new TrafficRun.Limits(1, 31, 1), "Reject long unattended runs");
    rejects(() -> new TrafficRun.Limits(1, 1, 9), "Reject excessive concurrency");
    var release = new java.util.concurrent.CountDownLatch(1);
    try (var run = new TrafficRun(new TrafficRun.Limits(50, 2, 1), () -> {
      release.await();
      return new TrafficRun.Observation("read → reserve → release", 7, 3);
    })) {
      run.start();
      Thread.sleep(180);
      var busy = run.status();
      check(busy.offered() > 1 && busy.dropped() > 0 && busy.inFlight() == 1,
          "Slow target keeps offered arrivals visible and drops excess at concurrency cap");
      run.stop();
      long stopped = run.status().offered();
      release.countDown();
      Thread.sleep(100);
      check(run.status().offered() == stopped && !run.status().running(),
          "Stop prevents further dispatch");
      check(run.status().inFlight() == 0, "Stopped work drains");
      var finalStatus = run.status();
      check(finalStatus.offered() == finalStatus.completed() + finalStatus.failed()
          + finalStatus.dropped() + finalStatus.inFlight(),
          "Stopped run accounts for every offered arrival");
    } finally {
      release.countDown();
    }
    try (var finite = new TrafficRun(new TrafficRun.Limits(1, 1, 1), () -> {
      Thread.sleep(80);
      return new TrafficRun.Observation("read → reserve → release", 6, 2);
    })) {
      finite.start();
      Thread.sleep(1150);
      check(!finite.status().running() && finite.status().offered() == 1
          && finite.status().completed() == 1 && finite.status().failed() == 0,
          "Scheduled end lets accepted work finish without recording a false failure");
    }
  }

  static void apiPoolTest() throws Exception {
    var ready = new java.util.concurrent.CountDownLatch(1);
    var starting = new java.util.concurrent.CountDownLatch(1);
    var owned = child();
    try (var pool = new ApiPool(8081, port -> {
      starting.countDown();
      ready.await();
      return owned;
    }); var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      var added = executor.submit(() -> { pool.add(); return true; });
      starting.await();
      check(pool.instances() == 1 && pool.nextPort() == 8081,
          "Unready extra instance receives no traffic");
      ready.countDown();
      added.get(2, java.util.concurrent.TimeUnit.SECONDS);
      check(Set.of(pool.nextPort(), pool.nextPort()).equals(Set.of(8081, 8082)),
          "Ready owned instances share consecutive requests");
      pool.remove();
      check(!owned.isAlive() && pool.instances() == 1 && pool.nextPort() == 8081,
          "Removing the owned extra instance restores the primary route");
    } finally { ready.countDown(); owned.destroy(); }
  }

  static void trafficTargetTest() throws Exception {
    var server = com.sun.net.httpserver.HttpServer.create(
        new java.net.InetSocketAddress("127.0.0.1", 0), 8);
    var paths = new java.util.concurrent.CopyOnWriteArrayList<String>();
    server.createContext("/", exchange -> {
      paths.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
      String body = exchange.getRequestURI().getPath().endsWith("/release")
          ? "{\"state\":\"RELEASED\"}"
          : exchange.getRequestURI().getPath().equals("/v1/reservations")
              ? "{\"id\" : \"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee5\"}"
              : "{\"available\" : 9, \"version\" : 3}";
      byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length);
      try (var out = exchange.getResponseBody()) { out.write(bytes); }
    });
    server.start();
    try {
      var target = new TrafficTarget(server.getAddress().getPort(), "local-token",
          UUID.fromString("cccccccc-cccc-4ccc-8ccc-ccccccccccc3"), "00123");
      var observation = target.perform();
      check(observation.available() == 9 && observation.version() == 3,
          "Traffic reports the stock value returned by the real HTTP read");
      check(paths.size() == 4 && paths.get(0).contains("/stock/00123")
          && paths.get(1).equals("POST /v1/reservations")
          && paths.get(2).endsWith("/release") && paths.get(3).contains("/stock/00123"),
          "A cycle reads, reserves and releases through the owned API");
    } finally {
      server.stop(0);
    }
  }

  static void trafficControlTest() throws Exception {
    var owned = child();
    var pool = new ApiPool(8081, port -> owned);
    var control = new TrafficControl(() -> new TrafficRun.Observation("read → reserve → release", 8, 4), pool);
    var server = Preview.create(java.nio.file.Path.of("."), 0, control);
    server.start();
    try (var client = java.net.http.HttpClient.newHttpClient()) {
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      var start = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base
          + "/lab/traffic/start?rate=5&seconds=2&concurrency=1"))
          .header("Origin", "http://localhost:" + server.getAddress().getPort())
          .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
      check(client.send(start, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode() == 200,
          "Localhost browser origin starts a bounded run");
      var status = client.send(java.net.http.HttpRequest.newBuilder(
          java.net.URI.create(base + "/lab/traffic")).build(),
          java.net.http.HttpResponse.BodyHandlers.ofString());
      check(status.body().contains("\"running\":true") && status.body().contains("\"offered\":"),
          "Status exposes actual run counters");
      check(client.send(start, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode() == 409,
          "Cannot start a second active run");
      var hostile = java.net.http.HttpRequest.newBuilder(start.uri())
          .header("Origin", "https://elsewhere.example")
          .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
      check(client.send(hostile, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode() == 403,
          "Foreign browser origins cannot control local traffic");
      var wrongPort = java.net.http.HttpRequest.newBuilder(start.uri())
          .header("Origin", "http://localhost:1")
          .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
      check(client.send(wrongPort, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode() == 403,
          "Other localhost ports cannot control traffic");
      var add = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/lab/traffic/add-instance"))
          .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
      check(client.send(add, java.net.http.HttpResponse.BodyHandlers.ofString()).body()
          .contains("\"instances\":2"), "HTTP control adds one ready owned instance");
      var remove = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/lab/traffic/remove-instance"))
          .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
      check(client.send(remove, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode() == 409,
          "Removal is refused until the bounded workload drains");
      var stop = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/lab/traffic/stop"))
          .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
      check(client.send(stop, java.net.http.HttpResponse.BodyHandlers.ofString()).body()
          .contains("\"running\":false"), "Stop reports the final status");
      check(client.send(remove, java.net.http.HttpResponse.BodyHandlers.ofString()).body()
          .contains("\"instances\":1") && !owned.isAlive(), "HTTP removal stops only the extra child");
    } finally {
      control.close();
      server.stop(0);
      ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
    }
  }

  static void apiReaderCredentialsTest() throws Exception {
    var credentials = java.nio.file.Files.createTempFile("stockflow-reader-", ".properties");
    try {
      java.nio.file.Files.writeString(
          credentials, "role=stockflow_catalog_reader\npassword=local-test-secret\n");
      java.nio.file.Files.setPosixFilePermissions(
          credentials,
          java.util.Set.of(
              java.nio.file.attribute.PosixFilePermission.OWNER_READ,
              java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
      var builder = new ProcessBuilder("true");
      Api.configureCatalog(
          builder, Database.config("postgresql://owner:owner-secret@localhost:5432/postgres", false),
          credentials);
      check(
          "stockflow_catalog_reader".equals(builder.environment().get("STOCKFLOW_JDBC_USER")),
          "API must use the catalog reader rather than the bootstrap owner");
      check(
          "local-test-secret".equals(builder.environment().get("STOCKFLOW_JDBC_PASSWORD")),
          "API receives the catalog reader secret");
    } finally {
      java.nio.file.Files.deleteIfExists(credentials);
    }
  }

  static void apiPortTest() throws Exception {
    try (var listener =
        new java.net.ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"))) {
      var builder =
          new ProcessBuilder(
              Api.java(), "-cp", System.getProperty("java.class.path"), "stockflow.Lab", "api");
      builder.environment().put("API_PORT", Integer.toString(listener.getLocalPort()));
      var process = builder.redirectErrorStream(true).start();
      check(
          process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS),
          "API port check exits promptly");
      String output =
          new String(
              process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      check(
          process.exitValue() != 0 && output.contains("already in use"),
          "Occupied API port gives actionable guidance");
      check(!listener.isClosed(), "API startup leaves the existing listener alone");
    }
  }

  static void apiArtifactTest() throws Exception {
    var directory = java.nio.file.Files.createTempDirectory("stockflow-artifact-");
    var source = directory.resolve("build.jar");
    java.nio.file.Path snapshot = null;
    try {
      java.nio.file.Files.writeString(source, "first build");
      snapshot = Api.snapshot(source, directory);
      java.nio.file.Files.writeString(source, "new build with different bytes");
      check(
          java.nio.file.Files.readString(snapshot).equals("first build"),
          "A running API must retain its artifact when Maven replaces the build output");
    } finally {
      if (snapshot != null) java.nio.file.Files.deleteIfExists(snapshot);
      java.nio.file.Files.deleteIfExists(source);
      java.nio.file.Files.delete(directory);
    }
  }

  static Process child() throws Exception {
    var process =
        new ProcessBuilder(
                java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                System.getProperty("java.class.path"),
                "stockflow.ToolTests",
                "child")
            .start();
    String ready =
        new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))
            .readLine();
    check("ready".equals(ready), "Owned child starts");
    return process;
  }

  static void previewTests() throws Exception {
    var dir = java.nio.file.Files.createTempDirectory("stockflow-stop-");
    var child = child();
    try {
      Preview.register(dir, 4175, child.toHandle());
      var path = Preview.recordPath(dir, 4175);
      var record = new Properties();
      try (var reader = java.nio.file.Files.newBufferedReader(path)) {
        record.load(reader);
      }
      record.setProperty("started", "stale identity");
      try (var writer = java.nio.file.Files.newBufferedWriter(path)) {
        record.store(writer, "");
      }
      check(
          Preview.stop(dir, 4175).contains("mismatch") && child.isAlive(),
          "Stale identity never stops a process");
      Preview.register(dir, 4175, child.toHandle());
      check(
          Preview.stop(dir, 4175).contains("stopped") && !child.isAlive(),
          "Stop only the registered process");
      check(Preview.stop(dir, 4175).contains("No registered"), "Repeated stop is harmless");
    } finally {
      child.destroy();
      java.nio.file.Files.deleteIfExists(Preview.recordPath(dir, 4175));
      java.nio.file.Files.delete(dir);
    }
    try (var listener =
        new java.net.ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"))) {
      boolean occupied = false;
      try {
        Preview.create(java.nio.file.Path.of("."), listener.getLocalPort());
      } catch (java.net.BindException expected) {
        occupied = true;
      }
      check(occupied && !listener.isClosed(), "Occupied listener is never killed or replaced");
    }
    var server = Preview.create(java.nio.file.Path.of("."), 0);
    server.start();
    try (var client = java.net.http.HttpClient.newHttpClient()) {
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      for (var item : Map.of("/", 200, "/.env", 404, "/../.env", 404).entrySet()) {
        var response =
            client.send(
                java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + item.getKey()))
                    .build(),
                java.net.http.HttpResponse.BodyHandlers.discarding());
        check(
            response.statusCode() == item.getValue(),
            "Explicit HTTP file allowlist: " + item.getKey());
      }
      var response =
          client.send(
              java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/"))
                  .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                  .build(),
              java.net.http.HttpResponse.BodyHandlers.discarding());
      check(response.statusCode() == 405, "Reject HTTP mutation requests");
    } finally {
      server.stop(0);
      ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
    }
  }
}
