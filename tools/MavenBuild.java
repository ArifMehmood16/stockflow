import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Pinned Maven launched with Java: no installed Maven or shell wrapper required. */
public class MavenBuild {
  static final String VERSION = "3.9.11";
  static final String SHA512 =
      "03e2d65d4483a3396980629f260e25cac0d8b6f7f2791e4dc20bc83f9514db8d0f05b0479e699a5f34679250c49c8e52e961262ded468a20de0be254d8207076";

  public static void main(String[] args) throws Exception {
    if (Runtime.version().feature() != 25)
      throw new IllegalStateException("Service build requires JDK 25; set JAVA_HOME.");
    Path cache = Path.of(".lab").toAbsolutePath();
    Files.createDirectories(cache);
    Path archive = cache.resolve("apache-maven-" + VERSION + "-bin.zip");
    if (!Files.exists(archive)) {
      Path part = cache.resolve("maven-download.partial");
      try (var client = HttpClient.newHttpClient()) {
        var request =
            HttpRequest.newBuilder(
                    URI.create(
                        "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/"
                            + VERSION
                            + "/apache-maven-"
                            + VERSION
                            + "-bin.zip"))
                .timeout(Duration.ofMinutes(2))
                .build();
        if (client.send(request, HttpResponse.BodyHandlers.ofFile(part)).statusCode() != 200) {
          Files.deleteIfExists(part);
          throw new IllegalStateException("Maven download failed.");
        }
      }
      Files.move(part, archive, StandardCopyOption.REPLACE_EXISTING);
    }
    var digest = MessageDigest.getInstance("SHA-512");
    try (var input = Files.newInputStream(archive)) {
      byte[] buffer = new byte[65536];
      for (int n; (n = input.read(buffer)) != -1; ) digest.update(buffer, 0, n);
    }
    if (!SHA512.equals(HexFormat.of().formatHex(digest.digest())))
      throw new IllegalStateException(
          "Maven checksum mismatch; remove the cached archive and retry.");
    Path home = cache.resolve("apache-maven-" + VERSION);
    // Extract the verified archive on each invocation; refuse traversal outside its own directory.
    try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
      for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        Path output = cache.resolve(entry.getName()).normalize();
        if (!output.startsWith(home))
          throw new IllegalStateException("Invalid Maven archive entry.");
        if (entry.isDirectory()) Files.createDirectories(output);
        else {
          Files.createDirectories(output.getParent());
          Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
    Path boot;
    try (var files = Files.list(home.resolve("boot"))) {
      boot = files.filter(p -> p.toString().endsWith(".jar")).findFirst().orElseThrow();
    }
    var command =
        new ArrayList<>(
            List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx512m",
                "-Dmaven.home=" + home,
                "-Dmaven.multiModuleProjectDirectory=" + Path.of("").toAbsolutePath(),
                "-Dclassworlds.conf=" + home.resolve("bin/m2.conf"),
                "-cp",
                boot.toString(),
                "org.codehaus.plexus.classworlds.launcher.Launcher",
                "--batch-mode",
                "--no-transfer-progress",
                "-Dmaven.repo.local=" + cache.resolve("m2")));
    command.addAll(Arrays.asList(args));
    System.exit(new ProcessBuilder(command).inheritIO().start().waitFor());
  }
}
