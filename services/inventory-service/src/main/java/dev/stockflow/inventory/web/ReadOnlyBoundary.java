package dev.stockflow.inventory.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class ReadOnlyBoundary extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = UUID.randomUUID().toString();
    request.setAttribute("stockflow.requestId", id);
    response.setHeader("X-Request-Id", id);
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
    if (!Set.of("localhost", "127.0.0.1", "[::1]", "::1", "inventory")
        .contains(request.getServerName())) {
      reject(response, 400, "INVALID_HOST", id);
    } else if (allowedPost(request) && request.getHeader("Origin") != null) {
      reject(response, 403, "ORIGIN_REJECTED", id);
    } else if (allowedPost(request)
        && ("/v1/reservations".equals(request.getRequestURI())
            ? request.getContentLengthLong() < 0 || request.getContentLengthLong() > 1024
            : request.getContentLengthLong() > 0
                || request.getHeader("Transfer-Encoding") != null)) {
      reject(response, 413, "REQUEST_REJECTED", id);
    } else if (!Set.of("GET", "HEAD").contains(request.getMethod())
        && !allowedPost(request)) {
      response.setHeader("Allow", "GET, HEAD");
      reject(response, 405, "METHOD_NOT_ALLOWED", id);
    } else chain.doFilter(request, response);
  }

  private boolean allowedPost(HttpServletRequest request) {
    return "POST".equals(request.getMethod())
        && ("/v1/reservations".equals(request.getRequestURI())
            || request.getRequestURI().matches(
                "/v1/reservations/[0-9a-fA-F-]{36}/release"));
  }

  private void reject(HttpServletResponse response, int status, String code, String id)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response
        .getWriter()
        .write(
            "{\"code\":\""
                + code
                + "\",\"message\":\"Request is not allowed.\",\"requestId\":\""
                + id
                + "\",\"retryable\":false}");
  }
}
