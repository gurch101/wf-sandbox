package com.gurch.sandbox.idempotency.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.idempotency.IdempotencyConflictException;
import com.gurch.sandbox.idempotency.MissingIdempotencyKeyException;
import com.gurch.sandbox.idempotency.internal.models.IdempotencyRecordEntity;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Aspect for handling idempotency globally for POST, PUT, and DELETE requests. This aspect runs
 * with high precedence to be outside of the primary transaction.
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdempotencyAspect {

  private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

  private final IdempotencyService service;
  private final ObjectMapper objectMapper;

  /**
   * Around advice for controller methods.
   *
   * @param joinPoint the join point
   * @return the result
   * @throws Throwable if an error occurs
   */
  @Around(
      "(execution(@org.springframework.web.bind.annotation.PostMapping * *(..)) || "
          + "execution(@org.springframework.web.bind.annotation.PutMapping * *(..)) || "
          + "execution(@org.springframework.web.bind.annotation.DeleteMapping * *(..))) "
          + "&& !@annotation(com.gurch.sandbox.idempotency.NotIdempotent)")
  public Object handleIdempotency(ProceedingJoinPoint joinPoint) throws Throwable {
    HttpServletRequest request = getRequest();
    if (request == null) {
      return joinPoint.proceed();
    }

    String key = getIdempotencyKey(request);
    String operation = resolveOperation(request);
    String payloadHash = calculateHash(joinPoint);

    Optional<IdempotencyRecordEntity> existingRecord = service.findRecord(key, operation);
    if (existingRecord.isPresent()) {
      return processExistingRecord(existingRecord.get(), payloadHash, key, operation, joinPoint);
    }

    return executeAndStore(joinPoint, key, operation, payloadHash);
  }

  private HttpServletRequest getRequest() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    return attributes != null ? attributes.getRequest() : null;
  }

  private String getIdempotencyKey(HttpServletRequest request) {
    String key = request.getHeader(IDEMPOTENCY_HEADER);
    if (key == null || key.isBlank()) {
      log.warn("Missing Idempotency-Key for {} {}", request.getMethod(), request.getRequestURI());
      throw new MissingIdempotencyKeyException(
          "Idempotency-Key header is required for this operation");
    }
    return key;
  }

  private String resolveOperation(HttpServletRequest request) {
    return request.getMethod() + " " + request.getRequestURI();
  }

  private Object processExistingRecord(
      IdempotencyRecordEntity record,
      String payloadHash,
      String key,
      String operation,
      ProceedingJoinPoint joinPoint)
      throws Exception {
    validateHash(record, payloadHash, key, operation);
    ensureNotProcessing(record, key, operation);

    log.info("Returning idempotent response for key {} and operation {}", key, operation);
    return replayResponse(joinPoint, record);
  }

  private void validateHash(
      IdempotencyRecordEntity record, String payloadHash, String key, String operation) {
    if (!record.getRequestHash().equals(payloadHash)) {
      log.warn(
          "Idempotency conflict for key {} and operation {}: payload hash mismatch",
          key,
          operation);
      throw new IdempotencyConflictException("Idempotency-Key conflict: payload mismatch");
    }
  }

  private void ensureNotProcessing(IdempotencyRecordEntity record, String key, String operation) {
    if (IdempotencyStatus.PROCESSING.equals(record.getStatus())) {
      log.warn(
          "Idempotency conflict for key {} and operation {}: already processing", key, operation);
      throw new IdempotencyConflictException(
          "Request with this Idempotency-Key is already being processed");
    }
  }

  private Object executeAndStore(
      ProceedingJoinPoint joinPoint, String key, String operation, String payloadHash)
      throws Throwable {
    IdempotencyRecordEntity record =
        IdempotencyRecordEntity.builder()
            .idempotencyKey(key)
            .operation(operation)
            .requestHash(payloadHash)
            .status(IdempotencyStatus.PROCESSING)
            .build();

    try {
      record = service.startOperation(record);
    } catch (DataIntegrityViolationException e) {
      // Concurrent request might have won the race
      return service
          .findRecord(key, operation)
          .map(
              existing -> {
                try {
                  return processExistingRecord(existing, payloadHash, key, operation, joinPoint);
                } catch (Exception ex) {
                  throw new RuntimeException(ex);
                }
              })
          .orElseThrow(() -> e);
    }

    Object result;
    try {
      result = joinPoint.proceed();
    } catch (Throwable t) {
      service.deleteRecord(record);
      throw t;
    }

    updateRecordWithResult(record, joinPoint, result);
    return result;
  }

  private void updateRecordWithResult(
      IdempotencyRecordEntity record, ProceedingJoinPoint joinPoint, Object result)
      throws Exception {
    int httpStatus = resolveExpectedStatus(joinPoint);

    if (result instanceof ResponseEntity<?> responseEntity) {
      if (responseEntity.getStatusCode().is2xxSuccessful()) {
        storeSuccess(record, responseEntity.getStatusCode().value(), responseEntity.getBody());
      } else {
        service.deleteRecord(record);
      }
    } else {
      storeSuccess(record, httpStatus, result);
    }
  }

  private int resolveExpectedStatus(ProceedingJoinPoint joinPoint) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    ResponseStatus ann = method.getAnnotation(ResponseStatus.class);
    return ann != null ? ann.value().value() : HttpStatus.OK.value();
  }

  private void storeSuccess(IdempotencyRecordEntity record, int status, Object body) {
    record.setStatus(IdempotencyStatus.COMPLETED);
    record.setResponseStatus(status);
    record.setResponseBody(objectMapper.valueToTree(body));
    service.completeOperation(record);
  }

  private String calculateHash(ProceedingJoinPoint joinPoint) throws Exception {
    Object payload = resolveRequestBody(joinPoint);
    if (payload == null) {
      return "";
    }

    byte[] bytes = objectMapper.writeValueAsBytes(payload);
    byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
    return Base64.getEncoder().encodeToString(hash);
  }

  private Object resolveRequestBody(ProceedingJoinPoint joinPoint) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Annotation[][] parameterAnnotations = signature.getMethod().getParameterAnnotations();
    Object[] args = joinPoint.getArgs();

    for (int i = 0; i < parameterAnnotations.length; i++) {
      for (Annotation annotation : parameterAnnotations[i]) {
        if (annotation instanceof RequestBody) {
          return args[i];
        }
      }
    }
    return null;
  }

  private Object replayResponse(ProceedingJoinPoint joinPoint, IdempotencyRecordEntity record)
      throws Exception {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Type returnType = signature.getMethod().getGenericReturnType();
    JsonNode bodyNode = record.getResponseBody();

    if (returnType instanceof ParameterizedType pt
        && pt.getRawType().equals(ResponseEntity.class)) {
      Type bodyType = pt.getActualTypeArguments()[0];
      JavaType javaType = objectMapper.getTypeFactory().constructType(bodyType);
      return ResponseEntity.status(record.getResponseStatus())
          .body(objectMapper.treeToValue(bodyNode, javaType));
    }

    if (signature.getReturnType().equals(ResponseEntity.class)) {
      return ResponseEntity.status(record.getResponseStatus()).body(bodyNode);
    }

    return objectMapper.treeToValue(bodyNode, signature.getReturnType());
  }
}
