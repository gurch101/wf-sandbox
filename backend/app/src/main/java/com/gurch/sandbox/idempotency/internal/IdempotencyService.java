package com.gurch.sandbox.idempotency.internal;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal service for managing idempotency records with isolated transactions. */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

  private final IdempotencyRepository repository;

  /**
   * Finds an existing record.
   *
   * @param key the idempotency key
   * @param operation the operation
   * @return the record if found
   */
  @Transactional(readOnly = true)
  public Optional<IdempotencyRecordEntity> findRecord(String key, String operation) {
    return repository.findByIdempotencyKeyAndOperation(key, operation);
  }

  /**
   * Starts a new idempotent operation by saving a PROCESSING record. This uses a new transaction to
   * ensure visibility to concurrent requests.
   *
   * @param record the record to save
   * @return the saved record
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public IdempotencyRecordEntity startOperation(IdempotencyRecordEntity record) {
    return repository.save(record);
  }

  /**
   * Updates a record with the final result.
   *
   * @param record the record to update
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void completeOperation(IdempotencyRecordEntity record) {
    repository.save(record);
  }

  /**
   * Deletes a record, typically on failure.
   *
   * @param record the record to delete
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void deleteRecord(IdempotencyRecordEntity record) {
    repository.delete(record);
  }
}
