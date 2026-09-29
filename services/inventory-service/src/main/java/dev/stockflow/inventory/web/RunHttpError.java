package dev.stockflow.inventory.web;

public final class RunHttpError extends RuntimeException {
  public final int status;
  public final String code;
  public final boolean retryable;

  public RunHttpError(int status, String code, boolean retryable) {
    this.status = status;
    this.code = code;
    this.retryable = retryable;
  }
}
