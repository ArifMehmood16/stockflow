package dev.stockflow.inventory.application;

public final class RunFailure extends RuntimeException {
  public final int status;
  public final String code;
  public final boolean retryable;

  public RunFailure(int status, String code, boolean retryable) {
    this.status = status;
    this.code = code;
    this.retryable = retryable;
  }
}
