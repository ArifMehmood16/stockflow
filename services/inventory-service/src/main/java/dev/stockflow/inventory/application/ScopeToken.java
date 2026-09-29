package dev.stockflow.inventory.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Strict, versioned local-fixture credentials shared by the Java CLI and service. */
public final class ScopeToken {
  private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
  private static final Pattern TENANT = Pattern.compile(
      "\\{\\\"exp\\\":([0-9]+),\\\"runId\\\":\\\"(" + UUID_PATTERN
          + ")\\\",\\\"tenantId\\\":\\\"(" + UUID_PATTERN
          + ")\\\",\\\"typ\\\":\\\"tenant\\\"\\}");
  private static final Pattern SESSION = Pattern.compile(
      "\\{\\\"exp\\\":([0-9]+),\\\"minVersion\\\":([0-9]+),"
          + "\\\"runId\\\":\\\"(" + UUID_PATTERN + ")\\\","
          + "\\\"sku\\\":\\\"([0-9]{1,32})\\\","
          + "\\\"tenantId\\\":\\\"(" + UUID_PATTERN + ")\\\","
          + "\\\"typ\\\":\\\"session\\\","
          + "\\\"warehouseId\\\":\\\"(" + UUID_PATTERN + ")\\\"\\}");

  private ScopeToken() {}

  public record TenantScope(UUID runId, UUID tenantId, Instant expiresAt) {}

  public record SessionScope(UUID runId, UUID tenantId, UUID warehouseId,
      String sku, long minVersion, Instant expiresAt) {}

  public static String issueTenant(UUID runId, UUID tenantId, Instant expiresAt, byte[] key) {
    String payload = "{\"exp\":" + expiresAt.getEpochSecond() + ",\"runId\":\""
        + runId + "\",\"tenantId\":\"" + tenantId + "\",\"typ\":\"tenant\"}";
    return sign(payload, key);
  }

  public static String issueSession(UUID runId, UUID tenantId, UUID warehouseId,
      String sku, long minVersion, Instant expiresAt, byte[] key) {
    if (!sku.matches("[0-9]{1,32}") || minVersion < 0)
      throw new IllegalArgumentException("Invalid session claim.");
    String payload = "{\"exp\":" + expiresAt.getEpochSecond()
        + ",\"minVersion\":" + minVersion + ",\"runId\":\"" + runId
        + "\",\"sku\":\"" + sku + "\",\"tenantId\":\"" + tenantId
        + "\",\"typ\":\"session\",\"warehouseId\":\"" + warehouseId + "\"}";
    return sign(payload, key);
  }

  public static TenantScope verifyTenant(String token, byte[] key, Instant now) {
    var claim = TENANT.matcher(payload(token, key));
    if (!claim.matches()) throw new IllegalArgumentException("Invalid credential.");
    Instant expires = expiry(claim.group(1), now);
    return new TenantScope(UUID.fromString(claim.group(2)), UUID.fromString(claim.group(3)), expires);
  }

  public static SessionScope verifySession(String token, byte[] key, Instant now) {
    var claim = SESSION.matcher(payload(token, key));
    if (!claim.matches()) throw new IllegalArgumentException("Invalid credential.");
    Instant expires = expiry(claim.group(1), now);
    try {
      return new SessionScope(UUID.fromString(claim.group(3)), UUID.fromString(claim.group(5)),
          UUID.fromString(claim.group(6)), claim.group(4), Long.parseLong(claim.group(2)), expires);
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException("Invalid credential.", invalid);
    }
  }

  private static Instant expiry(String seconds, Instant now) {
    try {
      Instant value = Instant.ofEpochSecond(Long.parseLong(seconds));
      if (!value.isAfter(now)) throw new IllegalArgumentException("Credential expired.");
      return value;
    } catch (NumberFormatException | java.time.DateTimeException invalid) {
      throw new IllegalArgumentException("Invalid credential.", invalid);
    }
  }

  private static String sign(String payload, byte[] key) {
    byte[] encoded = payload.getBytes(StandardCharsets.UTF_8);
    String body = Base64.getUrlEncoder().withoutPadding().encodeToString(encoded);
    return "sf1." + body + "."
        + Base64.getUrlEncoder().withoutPadding().encodeToString(mac(encoded, key));
  }

  private static String payload(String token, byte[] key) {
    if (token == null || token.length() > 1024) throw new IllegalArgumentException("Invalid credential.");
    String[] parts = token.split("\\.", -1);
    if (parts.length != 3 || !"sf1".equals(parts[0]))
      throw new IllegalArgumentException("Invalid credential.");
    try {
      byte[] body = Base64.getUrlDecoder().decode(parts[1]);
      byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
      if (signature.length != 32
          || !parts[1].equals(Base64.getUrlEncoder().withoutPadding().encodeToString(body))
          || !parts[2].equals(Base64.getUrlEncoder().withoutPadding().encodeToString(signature))
          || !MessageDigest.isEqual(mac(body, key), signature))
        throw new IllegalArgumentException("Invalid credential.");
      String decoded = new String(body, StandardCharsets.UTF_8);
      if (!java.util.Arrays.equals(decoded.getBytes(StandardCharsets.UTF_8), body))
        throw new IllegalArgumentException("Invalid credential.");
      return decoded;
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("Invalid credential.", invalid);
    }
  }

  private static byte[] mac(byte[] input, byte[] key) {
    if (key.length != 32) throw new IllegalArgumentException("Invalid credential key.");
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(input);
    } catch (java.security.GeneralSecurityException impossible) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable.", impossible);
    }
  }
}
