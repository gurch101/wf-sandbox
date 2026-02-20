package com.gurch.sandbox.auth;

import com.gurch.sandbox.auth.internal.PermissionEntity;
import com.gurch.sandbox.auth.internal.RoleEntity;
import com.gurch.sandbox.auth.internal.WorkflowGroupEntity;

final class AdminDtoMapper {

  private AdminDtoMapper() {}

  static AdminDtos.RoleResponse toRoleResponse(RoleEntity role) {
    return new AdminDtos.RoleResponse(
        role.id(), role.code(), role.name(), role.createdAt(), role.updatedAt());
  }

  static AdminDtos.PermissionResponse toPermissionResponse(PermissionEntity permission) {
    return new AdminDtos.PermissionResponse(
        permission.id(),
        permission.code(),
        permission.description(),
        permission.createdAt(),
        permission.updatedAt());
  }

  static AdminDtos.WorkflowGroupResponse toWorkflowGroupResponse(
      WorkflowGroupEntity workflowGroup) {
    return new AdminDtos.WorkflowGroupResponse(
        workflowGroup.id(),
        workflowGroup.code(),
        workflowGroup.name(),
        workflowGroup.createdAt(),
        workflowGroup.updatedAt());
  }
}
