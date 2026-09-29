package stockflow;

import java.util.concurrent.*;

/** One bounded, local experiment. A scheduled arrival never waits for a slow target. */
final class TrafficRun implements AutoCloseable {
  record Limits(int rate, int seconds, int concurrency) {
    Limits {
      if (rate < 1 || rate > 50 || seconds < 1 || seconds > 30
          || concurrency < 1 || concurrency > 8)
        throw new IllegalArgumentException("Traffic limits: rate 1..50/s, duration 1..30s, concurrency 1..8.");
    }
  }

  record Observation(String path, int available, long version) {}
  record Status(boolean running, long offered, long started, long completed, long failed,
      long dropped, int inFlight, String lastPath, long lastElapsedMillis,
      int available, long version, String lastError) {}
  @FunctionalInterface interface Operation { Observation perform() throws Exception; }

  private final Limits limits;
  private final Operation operation;
  private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
  private final ExecutorService workers;
  private boolean running;
  private long offered, started, completed, failed, dropped, lastElapsedMillis;
  private int inFlight, available;
  private long version;
  private String lastPath = "";
  private String lastError = "";

  TrafficRun(Limits limits, Operation operation) {
    this.limits = limits;
    this.operation = operation;
    workers = Executors.newFixedThreadPool(limits.concurrency());
  }

  synchronized void start() {
    if (running || offered != 0) throw new IllegalStateException("Traffic run already started.");
    running = true;
    timer.scheduleAtFixedRate(this::arrive, 0, 1000L / limits.rate(), TimeUnit.MILLISECONDS);
    timer.schedule(this::finish, limits.seconds(), TimeUnit.SECONDS);
  }

  private synchronized void arrive() {
    if (!running) return;
    if (offered >= (long) limits.rate() * limits.seconds()) return;
    offered++;
    if (inFlight >= limits.concurrency()) {
      dropped++;
      return;
    }
    inFlight++;
    started++;
    workers.execute(() -> {
      long begin = System.nanoTime();
      try {
        Observation observed = operation.perform();
        synchronized (this) {
          completed++;
          lastPath = observed.path();
          available = observed.available();
          version = observed.version();
          lastElapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
        }
      } catch (Exception error) {
        synchronized (this) {
          failed++;
          lastError = error instanceof IllegalStateException ? error.getMessage()
              : error.getClass().getSimpleName();
        }
      } finally {
        synchronized (this) { inFlight--; }
      }
    });
  }

  synchronized Status status() {
    return new Status(running, offered, started, completed, failed, dropped, inFlight,
        lastPath, lastElapsedMillis, available, version, lastError);
  }

  synchronized void stop() {
    running = false;
    timer.shutdownNow();
    int queued = workers.shutdownNow().size();
    inFlight -= queued;
    failed += queued;
  }

  private synchronized void finish() {
    running = false;
    timer.shutdown();
    workers.shutdown();
  }

  @Override public void close() {
    stop();
    try {
      workers.awaitTermination(2, TimeUnit.SECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
    }
  }
}
