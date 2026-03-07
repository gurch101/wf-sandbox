package com.gurch.sandbox.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Resolves correlation identifiers from explicit values or inbound request headers. */
@Component
public class CorrelationIdResolver {

  /**
   * Returns an explicit correlation id when provided, otherwise attempts to resolve from request
   * headers.
   *
   * @param explicitCorrelationId caller-provided id override
   * @return explicit id when present; otherwise resolved request header value; otherwise null
   */
  public String resolve(String explicitCorrelationId) {
    if (explicitCorrelationId != null && !explicitCorrelationId.isBlank()) {
      return explicitCorrelationId;
    }
    HttpServletRequest request = currentRequest();
    if (request == null) {
      return null;
    }
    return firstHeader(request, "X-Correlation-Id", "X-Request-Id", "Idempotency-Key");
  }

  private static HttpServletRequest currentRequest() {
    if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
      return null;
    }
    return attrs.getRequest();
  }

  private static String firstHeader(HttpServletRequest request, String... names) {
    for (String name : names) {
      String value = request.getHeader(name);
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }
}
