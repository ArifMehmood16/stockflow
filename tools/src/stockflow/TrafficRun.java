package stockflow;

import java.util.concurrent.*;

/** One bounded, local experiment. A scheduled arrival never waits for a slow target. */
final class TrafficRun implements AutoCloseable {
  record Limits(int rate, int seconds, int concurrency) {
    Limits {
      if (rate < 1 || rate > 10000 || seconds < 0 || seconds > 86400
          || concurrency < 1 || concurrency > 256)
        throw new IllegalArgumentException("Traffic limits: rate 1..10000 requests/s, duration 0 (until stopped)..86400s, concurrency 1..256.");
    }
  }

  record Observation(String path, int available, long version) {}
  record Status(boolean running, long offered, long started, long completed, long failed,
      long dropped, int inFlight, String lastPath, long lastElapsedMillis,
      int available, long version, String lastError, long elapsedMillis) {}
  @FunctionalInterface interface Operation { Observation perform() throws Exception; }

  private final Limits limits;
  private final Operation operation;
  private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
  private final ExecutorService workers;
  private boolean running;
  private long offered, started, completed, failed, dropped, lastElapsedMillis;
  private int inFlight, available;
  private long version;
  private long startedAt, stoppedAt;
  private String lastPath = "";
  private String lastError = "";

  TrafficRun(Limits limits, Operation operation) {
    this.limits = limits;
    this.operation = operation;
    workers = Executors.newVirtualThreadPerTaskExecutor();
  }

  synchronized void start() {
    if (running || offered != 0) throw new IllegalStateException("Traffic run already started.");
    running = true;
    startedAt = System.nanoTime();
    timer.scheduleAtFixedRate(this::arrive, 0, 10, TimeUnit.MILLISECONDS);
    if (limits.seconds() > 0) timer.schedule(this::finish, limits.seconds(), TimeUnit.SECONDS);
  }

  private synchronized void arrive() {
    if (!running) return;
    long due = 1 + (System.nanoTime() - startedAt) * limits.rate() / 1_000_000_000L;
    if (limits.seconds() > 0) due = Math.min(due, (long) limits.rate() * limits.seconds());
    long arrivals = Math.max(0, due - offered);
    int accepted = (int) Math.min(arrivals, limits.concurrency() - inFlight);
    offered += arrivals;
    dropped += arrivals - accepted;
    inFlight += accepted;
    started += accepted;
    for (int i = 0; i < accepted; i++) workers.execute(() -> {
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
        lastPath, lastElapsedMillis, available, version, lastError,
        startedAt == 0 ? 0 : TimeUnit.NANOSECONDS.toMillis((stoppedAt == 0 ? System.nanoTime() : stoppedAt) - startedAt));
  }

  synchronized void stop() {
    running = false;
    if (startedAt != 0 && stoppedAt == 0) stoppedAt = System.nanoTime();
    timer.shutdownNow();
    int queued = workers.shutdownNow().size();
    inFlight -= queued;
    failed += queued;
  }

  private synchronized void finish() {
    running = false;
    if (startedAt != 0 && stoppedAt == 0) stoppedAt = System.nanoTime();
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
