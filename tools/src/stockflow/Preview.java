package stockflow;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

final class Preview {
  static int port() {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "4173"));
    if (port < 1024 || port > 65535) throw new IllegalArgumentException("PORT must be 1024..65535");
    return port;
  }

  static Map<String, String> files() {
    var files = new HashMap<String, String>();
    files.put("/", "index.html");
    files.put("/index.html", "index.html");
    files.put("/favicon.svg", "favicon.svg");
    files.put("/styles.css", "styles.css");
    files.put("/app.mjs", "app.mjs");
    files.put("/model.mjs", "model.mjs");
    for (String name : List.of("java", "spring", "postgresql", "redis", "react", "nginx"))
      files.put("/assets/" + name + ".svg", "assets/" + name + ".svg");
    return Map.copyOf(files);
  }

  static Path recordPath(Path directory, int port) {
    return directory.resolve("preview-" + port + ".properties");
  }

  static void register(Path directory, int port, ProcessHandle process) throws IOException {
    Files.createDirectories(directory);
    var properties = new Properties();
    properties.setProperty("kind", "stockflow-java-preview");
    properties.setProperty("pid", Long.toString(process.pid()));
    properties.setProperty("started", process.info().startInstant().orElseThrow().toString());
    properties.setProperty("command", process.info().command().orElseThrow());
    Path file = recordPath(directory, port);
    try (var writer = Files.newBufferedWriter(file)) {
      properties.store(writer, "Owned StockFlow preview");
    }
    try {
      Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    } catch (UnsupportedOperationException ignored) {
      /* Windows uses inherited user ACL. */
    }
  }

  static String stop(Path directory, int port) throws Exception {
    Path file = recordPath(directory, port);
    if (!Files.exists(file))
      return "No registered Java preview on port "
          + port
          + ". Older Node previews need Ctrl-C once.";
    var record = new Properties();
    try (var reader = Files.newBufferedReader(file)) {
      record.load(reader);
    }
    if (!"stockflow-java-preview".equals(record.getProperty("kind")))
      return "Process identity mismatch; nothing stopped.";
    long pid = Long.parseLong(record.getProperty("pid", "0"));
    var process = ProcessHandle.of(pid);
    if (process.isEmpty() || !process.get().isAlive()) {
      Files.deleteIfExists(file);
      return "No registered preview is running.";
    }
    var info = process.get().info();
    if (pid <= 1
        || !info.startInstant()
            .map(Instant::toString)
            .orElse("")
            .equals(record.getProperty("started"))
        || !info.command().orElse("").equals(record.getProperty("command")))
      return "Process identity mismatch; nothing stopped.";
    process.get().destroy();
    try {
      process.get().onExit().get(5, TimeUnit.SECONDS);
    } catch (TimeoutException error) {
      return "Preview has not stopped yet; use Ctrl-C. No forced termination attempted.";
    }
    Files.deleteIfExists(file);
    return "StockFlow preview on port " + port + " stopped.";
  }

  static HttpServer create(Path root, int port) throws IOException {
    String host = System.getenv().getOrDefault("HOST", "127.0.0.1");
    var server = HttpServer.create(new InetSocketAddress(host, port), 32);
    var executor =
        new ThreadPoolExecutor(
            2,
            4,
            30,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32),
            new ThreadPoolExecutor.CallerRunsPolicy());
    server.setExecutor(executor);
    var files = files();
    server.createContext(
        "/",
        exchange -> {
          try {
            String method = exchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("HEAD")) {
              exchange.getResponseHeaders().set("Allow", "GET, HEAD");
              exchange.sendResponseHeaders(405, -1);
              return;
            }
            String target = files.get(exchange.getRequestURI().getRawPath());
            if (target == null) {
              exchange.sendResponseHeaders(404, -1);
              return;
            }
            byte[] bytes = Files.readAllBytes(root.resolve("prototype").resolve(target));
            var headers = exchange.getResponseHeaders();
            headers.set(
                "Content-Type",
                target.endsWith(".html")
                    ? "text/html; charset=utf-8"
                    : target.endsWith(".css")
                        ? "text/css; charset=utf-8"
                        : target.endsWith(".svg")
                            ? "image/svg+xml"
                            : "text/javascript; charset=utf-8");
            headers.set("Cache-Control", "no-store");
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("Referrer-Policy", "no-referrer");
            headers.set(
                "Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src"
                    + " 'self' data:; connect-src 'none'; frame-ancestors 'none'; base-uri 'none'");
            headers.set("Content-Length", Integer.toString(bytes.length));
            exchange.sendResponseHeaders(200, method.equals("HEAD") ? -1 : bytes.length);
            if (!method.equals("HEAD")) exchange.getResponseBody().write(bytes);
          } finally {
            exchange.close();
          }
        });
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  server.stop(0);
                  executor.shutdownNow();
                }));
    return server;
  }

  static void start(Path root, int port) throws Exception {
    HttpServer server;
    try {
      server = create(root, port);
    } catch (BindException error) {
      throw new IOException(
          "Port "
              + port
              + " is already in use. Open the existing preview, stop it in its terminal, or use"
              + " PORT="
              + (port == 65535 ? 4173 : port + 1)
              + " make preview.");
    }
    Path directory = root.resolve(".lab");
    if (!"1".equals(System.getenv("STOCKFLOW_DOCKER")))
      register(directory, port, ProcessHandle.current());
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    Path file = recordPath(directory, port);
                    if (Files.exists(file)) {
                      var record = new Properties();
                      try (var reader = Files.newBufferedReader(file)) {
                        record.load(reader);
                      }
                      if (Long.toString(ProcessHandle.current().pid())
                          .equals(record.getProperty("pid"))) Files.deleteIfExists(file);
                    }
                  } catch (IOException ignored) {
                    /* A later stop validates the saved process identity. */
                  }
                }));
    server.start();
    System.out.println("StockFlow Java preview: http://127.0.0.1:" + port);
  }
}
