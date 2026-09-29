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
    } finally {
      release.countDown();
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
