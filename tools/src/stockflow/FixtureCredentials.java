package stockflow;

import dev.stockflow.inventory.application.ScopeToken;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;

/** Trusted local issuer; HTTP handlers never mint tenant credentials. */
final class FixtureCredentials {
  private static final Set<PosixFilePermission> PRIVATE_FILE =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  static String issue(Connection connection, UUID runId, UUID tenantId) throws Exception {
    String schema = RunFixture.schema(runId);
    try (var query = connection.prepareStatement(
        "SELECT schema_name,state FROM stockflow_runs.ownership WHERE run_id=?")) {
      query.setObject(1, runId);
      try (var row = query.executeQuery()) {
        if (!row.next() || !schema.equals(row.getString(1)) || !"READY".equals(row.getString(2)))
          throw new IllegalArgumentException("Run fixture is not READY.");
      }
    }
    try (var query = connection.prepareStatement(
        "SELECT 1 FROM " + schema + ".inventory WHERE tenant_id=? LIMIT 1")) {
      query.setObject(1, tenantId);
      try (var row = query.executeQuery()) {
        if (!row.next()) throw new IllegalArgumentException("Tenant has no stock in this run.");
      }
    }
    return ScopeToken.issueTenant(runId, tenantId, Instant.now().plusSeconds(3600), key(runId));
  }

  static UUID tenantAt(Connection connection, UUID runId, int index) throws Exception {
    if (index < 0 || index > 1) throw new IllegalArgumentException("TENANT_INDEX must be 0 or 1.");
    try (var query = connection.prepareStatement(
        "SELECT seed,state FROM stockflow_runs.ownership WHERE run_id=?")) {
      query.setObject(1, runId);
      try (var row = query.executeQuery()) {
        if (!row.next() || !"READY".equals(row.getString(2)))
          throw new IllegalArgumentException("Run fixture is not READY.");
        return RunFixture.fixtureId(row.getString(1), "tenant", index);
      }
    }
  }

  static byte[] key(UUID runId) throws Exception {
    Path directory = Path.of(".lab/runs", runId.toString());
    Path path = directory.resolve("credential.key");
    Files.createDirectories(directory);
    if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
      byte[] generated = new byte[32];
      new SecureRandom().nextBytes(generated);
      Path temporary = Files.createTempFile(directory, "credential-", ".partial");
      try {
        Files.setPosixFilePermissions(temporary, PRIVATE_FILE);
        Files.write(temporary, generated, StandardOpenOption.TRUNCATE_EXISTING);
        try {
          Files.move(temporary, path);
        } catch (FileAlreadyExistsException existing) {
          Files.deleteIfExists(temporary);
        }
      } finally {
        Files.deleteIfExists(temporary);
      }
    }
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
        || Files.getPosixFilePermissions(path).stream()
            .anyMatch(permission -> !permission.name().startsWith("OWNER_")))
      throw new IllegalStateException("Run credential key must be an owner-only file.");
    byte[] stored = Files.readAllBytes(path);
    if (stored.length != 32) throw new IllegalStateException("Run credential key is invalid.");
    return stored;
  }
}
