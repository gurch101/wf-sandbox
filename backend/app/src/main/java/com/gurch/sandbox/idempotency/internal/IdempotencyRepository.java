package com.gurch.sandbox.idempotency.internal;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Repository for idempotency records. */
public interface IdempotencyRepository extends ListCrudRepository<IdempotencyRecordEntity, Long> {
  /**
   * Find a record by idempotency key and operation.
   *
   * @param idempotencyKey the key
   * @param operation the operation
   * @return the record if found
   */
  Optional<IdempotencyRecordEntity> findByIdempotencyKeyAndOperation(
      String idempotencyKey, String operation);

  /**
   * Delete records created before the given timestamp.
   *
   * @param threshold the timestamp
   */
  void deleteByCreatedAtBefore(Instant threshold);
}
