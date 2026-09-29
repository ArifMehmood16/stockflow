package stockflow;

import com.univocity.parsers.csv.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

final class Dataset {
  static final String SOURCE =
      "https://openfoodfacts-ds.s3.eu-west-3.amazonaws.com/en.openfoodfacts.org.products.csv.gz?versionId=vHh_.CO_011hmAhYIWJlhcqhlP4ecEyI";
  static final Path CACHE = Path.of(".lab/dataset");

  record Subset(Path path, int rows, String sha256, boolean complete) {
    Subset(Path path, int rows, String sha256) {
      this(path, rows, sha256, false);
    }
  }

  static String clean(String value, int limit) {
    String text = (value == null ? "" : value).replaceAll("[\\x00-\\x1f\\x7f]", " ");
    return text.substring(0, Math.min(limit, text.length()));
  }

  static String[] project(String[] row) {
    if (row.length < 4 || row[0] == null || !row[0].matches("[0-9]{1,32}")) return null;
    try {
      long seed =
          ByteBuffer.wrap(
                  MessageDigest.getInstance("SHA-256")
                      .digest(row[0].getBytes(StandardCharsets.UTF_8)))
              .getLong();
      long tenant = Long.remainderUnsigned(seed, 10000);
      return new String[] {
        row[0],
        clean(row[1] == null || row[1].isBlank() ? row[0] : row[1], 512),
        clean(row[2], 256),
        clean(row[3], 512),
        Long.toString(tenant),
        Long.toString(tenant % 16),
        Long.toString(10 + Long.remainderUnsigned(seed, 991))
      };
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  static String checksum(Path path) throws Exception {
    var digest = MessageDigest.getInstance("SHA-256");
    try (var stream = Files.newInputStream(path)) {
      byte[] buffer = new byte[65536];
      for (int n; (n = stream.read(buffer)) != -1; ) digest.update(buffer, 0, n);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  static int projectRows(Reader source, Writer output, int limit) {
    var settings = new CsvParserSettings();
    settings.getFormat().setDelimiter('\t');
    settings.setHeaderExtractionEnabled(true);
    settings.setMaxCharsPerColumn(2 * 1024 * 1024);
    settings.setMaxColumns(512);
    settings.setInputBufferSize(65536);
    settings.setReadInputOnSeparateThread(false);
    settings.selectFields("code", "product_name", "brands", "categories");
    var parser = new CsvParser(settings);
    var writer = new CsvWriter(output, writerSettings());
    var seen = new HashSet<String>();
    try {
      parser.beginParsing(source);
      for (int scanned = 0; scanned < 10000000; scanned++) {
        String[] row = parser.parseNext();
        if (row == null) {
          if (seen.isEmpty()) throw new IllegalArgumentException("Catalog has no valid products.");
          writer.flush();
          return seen.size();
        }
        String[] product = project(row);
        if (product == null || !seen.add(product[0])) continue;
        writer.writeRow((Object[]) product);
        if (seen.size() % 100000 == 0) System.out.printf("Selected %,d products…%n", seen.size());
        if (seen.size() == limit) {
          writer.flush();
          return seen.size();
        }
      }
      throw new IllegalArgumentException(
          "Catalog scan safety limit exceeded; no import performed.");
    } finally {
      parser.stopParsing();
      writer.flush();
    }
  }

  static CsvWriterSettings writerSettings() {
    var settings = new CsvWriterSettings();
    settings.getFormat().setLineSeparator("\n");
    // PostgreSQL treats any quote as CSV syntax, even inside an unquoted field.
    settings.setQuoteAllFields(true);
    settings.setQuoteEscapingEnabled(true);
    return settings;
  }

  static String normalizeCache(Path path, int expected) throws Exception {
    Path replacement = Path.of(path + ".normalized");
    var settings = new CsvParserSettings();
    settings.setMaxCharsPerColumn(2048);
    settings.setReadInputOnSeparateThread(false);
    var parser = new CsvParser(settings);
    int count = 0;
    try (var source = Files.newBufferedReader(path);
        var output = Files.newBufferedWriter(replacement)) {
      var writer = new CsvWriter(output, writerSettings());
      parser.beginParsing(source);
      for (String[] row; (row = parser.parseNext()) != null; ) {
        if (row.length != 7)
          throw new IOException("Cached CSV projection has an invalid field count.");
        writer.writeRow((Object[]) row);
        count++;
      }
      writer.flush();
      if (count != expected) throw new IOException("Cached CSV projection row count mismatch.");
    } finally {
      parser.stopParsing();
    }
    Files.move(replacement, path, StandardCopyOption.REPLACE_EXISTING);
    return checksum(path);
  }

  static Subset fetch(int limit) throws Exception {
    if (limit < 1 || limit > 10000000)
      throw new IllegalArgumentException("DATASET_ROWS must be between 1 and 10,000,000.");
    Files.createDirectories(CACHE);
    Path path = CACHE.resolve("products-" + limit + ".csv"),
        metadata = CACHE.resolve("products-" + limit + ".properties");
    if (Files.exists(path) && Files.exists(metadata)) {
      var properties = new Properties();
      try (var reader = Files.newBufferedReader(metadata)) {
        properties.load(reader);
      }
      String hash = checksum(path);
      int actual = Integer.parseInt(properties.getProperty("rows"));
      boolean complete = Boolean.parseBoolean(properties.getProperty("source_complete", "false"));
      if (!hash.equals(properties.getProperty("sha256"))
          || !SOURCE.equals(properties.getProperty("source_url"))
          || actual > limit
          || (actual < limit && !complete))
        throw new IllegalArgumentException(
            "Cached subset manifest/checksum failed. Remove only .lab/dataset/products-* to fetch"
                + " again.");
      if (!"2".equals(properties.getProperty("format_version"))) {
        System.out.println(
            "Upgrading cached CSV quoting for PostgreSQL COPY; source download is reused.");
        hash = normalizeCache(path, actual);
        properties.setProperty("sha256", hash);
        properties.setProperty("format_version", "2");
        try (var writer = Files.newBufferedWriter(metadata)) {
          properties.store(writer, "Verified PostgreSQL CSV projection");
        }
      }
      System.out.printf("Reusing verified catalog subset: %,d products.%n", actual);
      return new Subset(path, actual, hash, complete);
    }
    Path partial = CACHE.resolve("products-" + limit + ".partial");
    System.out.printf(
        "Streaming official Open Food Facts export; selecting %,d products…%n", limit);
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()) {
      var request =
          HttpRequest.newBuilder(URI.create(SOURCE))
              .header("User-Agent", "StockFlow/0.1 (github.com/ArifMehmood16/stockflow)")
              .timeout(Duration.ofMinutes(30))
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      int actual;
      try (var body = response.body()) {
        if (response.statusCode() != 200)
          throw new IOException("Catalog download failed with HTTP " + response.statusCode());
        try (var zipped = new GZIPInputStream(body);
            var reader = new InputStreamReader(zipped, StandardCharsets.UTF_8.newDecoder());
            var writer = Files.newBufferedWriter(partial)) {
          actual = projectRows(reader, writer, limit);
        }
      }
      String hash = checksum(partial);
      Files.move(partial, path, StandardCopyOption.REPLACE_EXISTING);
      var properties = new Properties();
      properties.setProperty("rows", Integer.toString(actual));
      properties.setProperty("requested_rows", Integer.toString(limit));
      properties.setProperty("source_complete", Boolean.toString(actual < limit));
      properties.setProperty("sha256", hash);
      properties.setProperty("source_url", SOURCE);
      properties.setProperty("format_version", "2");
      properties.setProperty("selected_at", Instant.now().toString());
      properties.setProperty("license", "ODbL-1.0; individual contents DbCL-1.0");
      properties.setProperty(
          "selection",
          "first valid unique numeric codes in pinned export order; synthetic inventory SHA-256 per"
              + " code");
      try (var writer = Files.newBufferedWriter(metadata)) {
        properties.store(writer, "Open Food Facts catalog subset");
      }
      System.out.println("Cached subset SHA-256: " + hash);
      System.out.printf(
          "Available selected products: %,d (requested maximum: %,d).%n", actual, limit);
      return new Subset(path, actual, hash, actual < limit);
    } finally {
      Files.deleteIfExists(partial);
    }
  }
}
