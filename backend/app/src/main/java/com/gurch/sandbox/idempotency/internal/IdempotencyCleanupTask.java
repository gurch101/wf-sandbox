package com.gurch.sandbox.idempotency.internal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Scheduled task to clean up old idempotency records. */
@Component
@Slf4j
@RequiredArgsConstructor
public class IdempotencyCleanupTask {

  private final IdempotencyRepository repository;

  /** Cleans up idempotency records older than 30 days. Runs daily at midnight. */
  @Scheduled(cron = "0 0 0 * * *")
  @Transactional
  public void cleanupOldRecords() {
    Instant threshold = Instant.now().minus(30, ChronoUnit.DAYS);
    log.info("Starting cleanup of idempotency records older than {}", threshold);
    repository.deleteByCreatedAtBefore(threshold);
    log.info("Cleanup of idempotency records completed");
  }
}
