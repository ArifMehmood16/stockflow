package dev.stockflow.inventory;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  @Test
  void coreBytecodeHasNoFrameworkOrPersistenceDependencies() throws Exception {
    String root = "target/classes/dev/stockflow/inventory/";
    var process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "jdeps").toString(),
                "--ignore-missing-deps",
                "-verbose:class",
                root + "domain",
                root + "application")
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, process.waitFor(), output);
    for (String forbidden :
        new String[] {
          "org.springframework",
          "java.sql.",
          "javax.sql.",
          "org.postgresql",
          "com.zaxxer",
          "jakarta.servlet",
          "dev.stockflow.inventory.adapter",
          "dev.stockflow.inventory.web"
        }) assertFalse(output.contains(forbidden), output);
  }
}
