package dev.stockflow.inventory.web;

import dev.stockflow.inventory.application.StockUnavailable;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public final class ApiErrors {
  public record ErrorResponse(String code, String message, String requestId, boolean retryable) {}

  private ResponseEntity<ErrorResponse> error(
      int status, String code, String message, boolean retryable, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(
            new ErrorResponse(
                code, message, (String) request.getAttribute("stockflow.requestId"), retryable));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> invalid(HttpServletRequest request) {
    return error(400, "INVALID_CODE", "Product code must contain 1–32 digits.", false, request);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ErrorResponse> missing(HttpServletRequest request) {
    return error(404, "STOCK_NOT_FOUND", "Product was not found.", false, request);
  }

  @ExceptionHandler(StockUnavailable.class)
  public ResponseEntity<ErrorResponse> unavailable(HttpServletRequest request) {
    return error(503, "STOCK_UNAVAILABLE", "Stock data is temporarily unavailable.", true, request);
  }

  @ExceptionHandler(SecurityException.class)
  public ResponseEntity<ErrorResponse> unauthorized(HttpServletRequest request) {
    return error(401, "UNAUTHORIZED", "Run credential is invalid or expired.", false, request);
  }

  @ExceptionHandler(RunHttpError.class)
  public ResponseEntity<ErrorResponse> runError(RunHttpError failure, HttpServletRequest request) {
    return error(failure.status, failure.code, failure.code, failure.retryable, request);
  }
}
