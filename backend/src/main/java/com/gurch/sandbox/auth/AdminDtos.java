package com.gurch.sandbox.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Value;

/** DTOs used by administrative RBAC and assignment endpoints. */
public interface AdminDtos {

  /** Request payload for creating a role. */
  @Value
  @Schema(description = "Request to create a role")
  class CreateRoleRequest {
    @NotBlank(message = "code is required")
    @Schema(description = "Role code", example = "REQUEST_OPERATOR")
    String code;

    @NotBlank(message = "name is required")
    @Schema(description = "Role display name", example = "Request Operator")
    String name;
  }

  /** Request payload for creating a permission. */
  @Value
  @Schema(description = "Request to create a permission")
  class CreatePermissionRequest {
    @NotBlank(message = "code is required")
    @Schema(description = "Permission code", example = "request.read")
    String code;

    @NotBlank(message = "description is required")
    @Schema(description = "Permission description", example = "Read requests")
    String description;
  }

  /** Request payload for creating a workflow group. */
  @Value
  @Schema(description = "Request to create a workflow group")
  class CreateWorkflowGroupRequest {
    @NotBlank(message = "code is required")
    @Schema(description = "Workflow group code", example = "WF_APPROVERS")
    String code;

    @NotBlank(message = "name is required")
    @Schema(description = "Workflow group display name", example = "Workflow Approvers")
    String name;
  }

  /** Request payload for updating role metadata. */
  @Value
  @Schema(description = "Request to update a role")
  class UpdateRoleRequest {
    @NotBlank(message = "name is required")
    @Schema(description = "Updated role display name", example = "Request Operator Updated")
    String name;
  }

  /** Request payload for updating permission metadata. */
  @Value
  @Schema(description = "Request to update a permission")
  class UpdatePermissionRequest {
    @NotBlank(message = "description is required")
    @Schema(description = "Updated permission description", example = "Read requests and metadata")
    String description;
  }

  /** Request payload for updating workflow group metadata. */
  @Value
  @Schema(description = "Request to update a workflow group")
  class UpdateWorkflowGroupRequest {
    @NotBlank(message = "name is required")
    @Schema(
        description = "Updated workflow group display name",
        example = "Workflow Approvers Updated")
    String name;
  }

  /** Response payload containing a created entity id. */
  @Value
  @Schema(description = "Created admin entity response")
  class CreatedAdminEntityResponse {
    @Schema(description = "Created entity identifier")
    UUID id;
  }

  /** Response payload with role details. */
  @Value
  @Schema(description = "Role details")
  class RoleResponse {
    @Schema(description = "Role identifier")
    UUID id;

    @Schema(description = "Role code")
    String code;

    @Schema(description = "Role display name")
    String name;

    @Schema(description = "Role creation timestamp")
    Instant createdAt;

    @Schema(description = "Role update timestamp")
    Instant updatedAt;
  }

  /** Paginated response for roles. */
  @Value
  @Schema(description = "Paginated role results")
  class RoleListResponse {
    @Schema(description = "Roles page")
    List<RoleResponse> roles;

    @Schema(description = "Total number of matching roles")
    long total;

    @Schema(description = "Zero-based page index")
    int page;

    @Schema(description = "Page size")
    int size;
  }

  /** Response payload with permission details. */
  @Value
  @Schema(description = "Permission details")
  class PermissionResponse {
    @Schema(description = "Permission identifier")
    UUID id;

    @Schema(description = "Permission code")
    String code;

    @Schema(description = "Permission description")
    String description;

    @Schema(description = "Permission creation timestamp")
    Instant createdAt;

    @Schema(description = "Permission update timestamp")
    Instant updatedAt;
  }

  /** Paginated response for permissions. */
  @Value
  @Schema(description = "Paginated permission results")
  class PermissionListResponse {
    @Schema(description = "Permissions page")
    List<PermissionResponse> permissions;

    @Schema(description = "Total number of matching permissions")
    long total;

    @Schema(description = "Zero-based page index")
    int page;

    @Schema(description = "Page size")
    int size;
  }

  /** Response payload with workflow group details. */
  @Value
  @Schema(description = "Workflow group details")
  class WorkflowGroupResponse {
    @Schema(description = "Workflow group identifier")
    UUID id;

    @Schema(description = "Workflow group code")
    String code;

    @Schema(description = "Workflow group name")
    String name;

    @Schema(description = "Workflow group creation timestamp")
    Instant createdAt;

    @Schema(description = "Workflow group update timestamp")
    Instant updatedAt;
  }

  /** Paginated response for workflow groups. */
  @Value
  @Schema(description = "Paginated workflow group results")
  class WorkflowGroupListResponse {
    @Schema(description = "Workflow groups page")
    List<WorkflowGroupResponse> workflowGroups;

    @Schema(description = "Total number of matching workflow groups")
    long total;

    @Schema(description = "Zero-based page index")
    int page;

    @Schema(description = "Page size")
    int size;
  }

  /** Response payload listing role codes assigned to a user. */
  @Value
  @Schema(description = "Role assignment list")
  class UserRoleAssignmentListResponse {
    @Schema(description = "Assigned role codes")
    List<String> roleCodes;
  }

  /** Response payload listing permission codes assigned to a role. */
  @Value
  @Schema(description = "Role-permission assignment list")
  class RolePermissionAssignmentListResponse {
    @Schema(description = "Assigned permission codes")
    List<String> permissionCodes;
  }

  /** Response payload listing workflow group codes assigned to a user. */
  @Value
  @Schema(description = "Workflow group assignment list")
  class UserWorkflowGroupAssignmentListResponse {
    @Schema(description = "Assigned workflow group codes")
    List<String> workflowGroupCodes;
  }

  /** Response payload listing business client scopes assigned to a user. */
  @Value
  @Schema(description = "Client scope assignment list")
  class UserClientScopeAssignmentListResponse {
    @Schema(description = "Assigned business client scope identifiers")
    List<String> clientScopeIds;
  }
}
