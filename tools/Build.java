import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.tools.ToolProvider;

/** Small Java-only bootstrap; the application will adopt Maven in its implementation phase. */
public class Build {
  record Dependency(String name, String path, String sha256) {}

  static final List<Dependency> DEPS =
      List.of(
          new Dependency(
              "postgresql-42.7.13.jar",
              "org/postgresql/postgresql/42.7.13/postgresql-42.7.13.jar",
              "6e0e4cc2d8cae902084f8a2b18728b073a6fd9d1f87c9d8bff8f298c18185b93"),
          new Dependency(
              "univocity-parsers-2.9.1.jar",
              "com/univocity/univocity-parsers/2.9.1/univocity-parsers-2.9.1.jar",
              "31685122d5e392e98672ed6009a95a4c1623ca1185567bd44ee94527d454e5c3"));

  static String checksum(Path path) throws Exception {
    var hash = MessageDigest.getInstance("SHA-256");
    try (var in = Files.newInputStream(path)) {
      byte[] buffer = new byte[65536];
      for (int n; (n = in.read(buffer)) != -1; ) hash.update(buffer, 0, n);
    }
    return HexFormat.of().formatHex(hash.digest());
  }

  public static void main(String[] args) throws Exception {
    if (Runtime.version().feature() < 25)
      throw new IllegalStateException("StockFlow requires JDK 25 or newer.");
    Files.createDirectories(Path.of(".lab/lib"));
    Files.createDirectories(Path.of(".lab/classes"));
    for (var dependency : DEPS) {
      Path jar = Path.of(".lab/lib", dependency.name());
      if (!Files.exists(jar)) {
        System.out.println("Downloading pinned dependency: " + dependency.name());
        var request =
            HttpRequest.newBuilder(
                    URI.create("https://repo.maven.apache.org/maven2/" + dependency.path()))
                .timeout(java.time.Duration.ofMinutes(2))
                .build();
        Path part = Path.of(jar + ".partial");
        try (var client = HttpClient.newHttpClient()) {
          var response = client.send(request, HttpResponse.BodyHandlers.ofFile(part));
          if (response.statusCode() != 200 || !checksum(part).equals(dependency.sha256())) {
            Files.deleteIfExists(part);
            throw new IllegalStateException("Dependency download/checksum failed.");
          }
        }
        Files.move(part, jar, StandardCopyOption.REPLACE_EXISTING);
      }
      if (!checksum(jar).equals(dependency.sha256()))
        throw new IllegalStateException("Dependency checksum mismatch: " + dependency.name());
    }
    var options =
        new ArrayList<>(
            List.of(
                "--release",
                "25",
                "-Xlint:all",
                "-cp",
                String.join(
                    java.io.File.pathSeparator,
                    DEPS.stream().map(d -> ".lab/lib/" + d.name()).toList()),
                "-d",
                ".lab/classes"));
    try (var files = Files.walk(Path.of("tools"))) {
      files
          .filter(p -> p.toString().endsWith(".java") && !p.equals(Path.of("tools/Build.java")))
          .forEach(p -> options.add(p.toString()));
    }
    options.add(
        "services/inventory-service/src/main/java/dev/stockflow/inventory/application/ScopeToken.java");
    if (ToolProvider.getSystemJavaCompiler().run(null, null, null, options.toArray(String[]::new))
        != 0) System.exit(1);
    String action = args.length == 0 ? "help" : args[0];
    if (action.equals("compile")) return;
    var command =
        new ArrayList<>(
            List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx768m",
                "-cp",
                ".lab/classes" + java.io.File.pathSeparator + ".lab/lib/*",
                action.equals("test") ? "stockflow.ToolTests" : "stockflow.Lab"));
    if (!action.equals("test")) command.addAll(Arrays.asList(args));
    System.exit(new ProcessBuilder(command).inheritIO().start().waitFor());
  }
}
