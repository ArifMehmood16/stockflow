package stockflow;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Owns at most one extra JVM. The starter returns only after readiness succeeds. */
final class ApiPool implements AutoCloseable {
  @FunctionalInterface interface Starter { Process start(int port) throws Exception; }
  private final int primary;
  private final Starter starter;
  private volatile Process extra;
  private volatile boolean building;
  private volatile boolean routable;
  private final AtomicLong sequence = new AtomicLong();
  final AtomicLong primaryRequests = new AtomicLong();
  final AtomicLong secondaryRequests = new AtomicLong();

  ApiPool(int primary, Starter starter) {
    this.primary = primary;
    this.starter = starter;
  }

  int instances() { return routable && extra != null && extra.isAlive() ? 2 : 1; }
  boolean building() { return building; }

  int nextPort() {
    if (instances() == 2 && (sequence.getAndIncrement() & 1) == 1) {
      secondaryRequests.incrementAndGet();
      return primary + 1;
    }
    primaryRequests.incrementAndGet();
    return primary;
  }

  synchronized void add() throws Exception {
    if (instances() == 2) return;
    if (extra != null && extra.isAlive()) throw new IllegalStateException("Extra API is still stopping.");
    if (primary == 65535) throw new IllegalStateException("No adjacent API port available.");
    building = true;
    try { extra = starter.start(primary + 1); routable = true; }
    finally { building = false; }
  }

  synchronized void remove() throws InterruptedException {
    Process owned = extra;
    if (owned == null) return;
    routable = false;
    owned.destroy();
    if (!owned.waitFor(7, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Owned API has not stopped yet.");
    }
    extra = null;
  }

  @Override public void close() {
    try { remove(); }
    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
  }
}
