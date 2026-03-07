package com.gurch.sandbox.audit;

/** Public audit-log API exposed to other modules. */
public interface AuditLogApi {

  /** Records a create operation for the resource snapshot after persistence. */
  default void recordCreate(String resourceType, Object resourceId, Object afterState) {
    recordCreate(resourceType, resourceId, afterState, null);
  }

  /** Records a create operation for the resource snapshot after persistence. */
  void recordCreate(
      String resourceType, Object resourceId, Object afterState, String correlationId);

  /** Records an update operation with before/after snapshots. */
  default void recordUpdate(
      String resourceType, Object resourceId, Object beforeState, Object afterState) {
    recordUpdate(resourceType, resourceId, beforeState, afterState, null);
  }

  /** Records an update operation with before/after snapshots. */
  void recordUpdate(
      String resourceType,
      Object resourceId,
      Object beforeState,
      Object afterState,
      String correlationId);

  /** Records a delete operation for the resource snapshot before removal. */
  default void recordDelete(String resourceType, Object resourceId, Object beforeState) {
    recordDelete(resourceType, resourceId, beforeState, null);
  }

  /** Records a delete operation for the resource snapshot before removal. */
  void recordDelete(
      String resourceType, Object resourceId, Object beforeState, String correlationId);
}
