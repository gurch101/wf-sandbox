package com.gurch.sandbox.auth;

import com.gurch.sandbox.auth.internal.AdminSecurityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Validated
@PreAuthorize("hasAuthority('admin.security.manage')")
public class AdminController {

  private final AdminSecurityService adminSecurityService;

  public AdminController(AdminSecurityService adminSecurityService) {
    this.adminSecurityService = adminSecurityService;
  }

  @PostMapping("/roles")
  @ResponseStatus(HttpStatus.CREATED)
  public AdminDtos.CreatedAdminEntityResponse createRole(
      @Valid @RequestBody AdminDtos.CreateRoleRequest request) {
    return new AdminDtos.CreatedAdminEntityResponse(
        adminSecurityService.createRole(request.getCode(), request.getName()));
  }

  @PostMapping("/permissions")
  @ResponseStatus(HttpStatus.CREATED)
  public AdminDtos.CreatedAdminEntityResponse createPermission(
      @Valid @RequestBody AdminDtos.CreatePermissionRequest request) {
    return new AdminDtos.CreatedAdminEntityResponse(
        adminSecurityService.createPermission(request.getCode(), request.getDescription()));
  }

  @PostMapping("/workflow-groups")
  @ResponseStatus(HttpStatus.CREATED)
  public AdminDtos.CreatedAdminEntityResponse createWorkflowGroup(
      @Valid @RequestBody AdminDtos.CreateWorkflowGroupRequest request) {
    return new AdminDtos.CreatedAdminEntityResponse(
        adminSecurityService.createWorkflowGroup(request.getCode(), request.getName()));
  }

  @GetMapping("/roles")
  public AdminDtos.RoleListResponse listRoles(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size,
      @RequestParam(required = false) String codeContains) {
    return new AdminDtos.RoleListResponse(
        adminSecurityService.listRoles(page, size, codeContains).stream()
            .map(this::toRoleResponse)
            .toList(),
        adminSecurityService.countRoles(codeContains),
        page,
        size);
  }

  @GetMapping("/roles/{roleCode}")
  public AdminDtos.RoleResponse getRoleByCode(@PathVariable String roleCode) {
    return toRoleResponse(adminSecurityService.getRoleByCode(roleCode));
  }

  @DeleteMapping("/roles/{roleCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteRoleByCode(@PathVariable String roleCode) {
    adminSecurityService.deleteRoleByCode(roleCode);
  }

  @PutMapping("/roles/{roleCode}")
  public AdminDtos.RoleResponse updateRole(
      @PathVariable String roleCode, @Valid @RequestBody AdminDtos.UpdateRoleRequest request) {
    return toRoleResponse(adminSecurityService.updateRole(roleCode, request.getName()));
  }

  @GetMapping("/permissions")
  public AdminDtos.PermissionListResponse listPermissions(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size,
      @RequestParam(required = false) String codeContains) {
    return new AdminDtos.PermissionListResponse(
        adminSecurityService.listPermissions(page, size, codeContains).stream()
            .map(this::toPermissionResponse)
            .toList(),
        adminSecurityService.countPermissions(codeContains),
        page,
        size);
  }

  @GetMapping("/permissions/{permissionCode}")
  public AdminDtos.PermissionResponse getPermissionByCode(@PathVariable String permissionCode) {
    return toPermissionResponse(adminSecurityService.getPermissionByCode(permissionCode));
  }

  @DeleteMapping("/permissions/{permissionCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletePermissionByCode(@PathVariable String permissionCode) {
    adminSecurityService.deletePermissionByCode(permissionCode);
  }

  @PutMapping("/permissions/{permissionCode}")
  public AdminDtos.PermissionResponse updatePermission(
      @PathVariable String permissionCode,
      @Valid @RequestBody AdminDtos.UpdatePermissionRequest request) {
    return toPermissionResponse(
        adminSecurityService.updatePermission(permissionCode, request.getDescription()));
  }

  @GetMapping("/workflow-groups")
  public AdminDtos.WorkflowGroupListResponse listWorkflowGroups(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size,
      @RequestParam(required = false) String codeContains) {
    return new AdminDtos.WorkflowGroupListResponse(
        adminSecurityService.listWorkflowGroups(page, size, codeContains).stream()
            .map(this::toWorkflowGroupResponse)
            .toList(),
        adminSecurityService.countWorkflowGroups(codeContains),
        page,
        size);
  }

  @GetMapping("/workflow-groups/{workflowGroupCode}")
  public AdminDtos.WorkflowGroupResponse getWorkflowGroupByCode(
      @PathVariable String workflowGroupCode) {
    return toWorkflowGroupResponse(adminSecurityService.getWorkflowGroupByCode(workflowGroupCode));
  }

  @DeleteMapping("/workflow-groups/{workflowGroupCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteWorkflowGroupByCode(@PathVariable String workflowGroupCode) {
    adminSecurityService.deleteWorkflowGroupByCode(workflowGroupCode);
  }

  @PutMapping("/workflow-groups/{workflowGroupCode}")
  public AdminDtos.WorkflowGroupResponse updateWorkflowGroup(
      @PathVariable String workflowGroupCode,
      @Valid @RequestBody AdminDtos.UpdateWorkflowGroupRequest request) {
    return toWorkflowGroupResponse(
        adminSecurityService.updateWorkflowGroup(workflowGroupCode, request.getName()));
  }

  @PutMapping("/roles/{roleCode}/permissions/{permissionCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void assignPermissionToRole(
      @PathVariable String roleCode, @PathVariable String permissionCode) {
    adminSecurityService.assignPermissionToRole(roleCode, permissionCode);
  }

  @GetMapping("/roles/{roleCode}/permissions")
  public AdminDtos.RolePermissionAssignmentListResponse listPermissionsAssignedToRole(
      @PathVariable String roleCode) {
    return new AdminDtos.RolePermissionAssignmentListResponse(
        adminSecurityService.listPermissionAssignmentsForRole(roleCode));
  }

  @DeleteMapping("/roles/{roleCode}/permissions/{permissionCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unassignPermissionFromRole(
      @PathVariable String roleCode, @PathVariable String permissionCode) {
    adminSecurityService.unassignPermissionFromRole(roleCode, permissionCode);
  }

  @PutMapping("/users/{userId}/roles/{roleCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void assignRoleToUser(@PathVariable UUID userId, @PathVariable String roleCode) {
    adminSecurityService.assignRoleToUser(userId, roleCode);
  }

  @GetMapping("/users/{userId}/roles")
  public AdminDtos.UserRoleAssignmentListResponse listRolesAssignedToUser(
      @PathVariable UUID userId) {
    return new AdminDtos.UserRoleAssignmentListResponse(
        adminSecurityService.listRoleAssignmentsForUser(userId));
  }

  @DeleteMapping("/users/{userId}/roles/{roleCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unassignRoleFromUser(@PathVariable UUID userId, @PathVariable String roleCode) {
    adminSecurityService.unassignRoleFromUser(userId, roleCode);
  }

  @PutMapping("/users/{userId}/workflow-groups/{workflowGroupCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void assignWorkflowGroupToUser(
      @PathVariable UUID userId, @PathVariable String workflowGroupCode) {
    adminSecurityService.assignWorkflowGroupToUser(userId, workflowGroupCode);
  }

  @GetMapping("/users/{userId}/workflow-groups")
  public AdminDtos.UserWorkflowGroupAssignmentListResponse listWorkflowGroupsAssignedToUser(
      @PathVariable UUID userId) {
    return new AdminDtos.UserWorkflowGroupAssignmentListResponse(
        adminSecurityService.listWorkflowGroupAssignmentsForUser(userId));
  }

  @DeleteMapping("/users/{userId}/workflow-groups/{workflowGroupCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unassignWorkflowGroupFromUser(
      @PathVariable UUID userId, @PathVariable String workflowGroupCode) {
    adminSecurityService.unassignWorkflowGroupFromUser(userId, workflowGroupCode);
  }

  @PutMapping("/users/{userId}/client-scopes/{businessClientId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void assignClientScopeToUser(
      @PathVariable UUID userId, @PathVariable String businessClientId) {
    adminSecurityService.assignClientScopeToUser(userId, businessClientId);
  }

  @GetMapping("/users/{userId}/client-scopes")
  public AdminDtos.UserClientScopeAssignmentListResponse listClientScopesAssignedToUser(
      @PathVariable UUID userId) {
    return new AdminDtos.UserClientScopeAssignmentListResponse(
        adminSecurityService.listClientScopeAssignmentsForUser(userId));
  }

  @DeleteMapping("/users/{userId}/client-scopes/{businessClientId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unassignClientScopeFromUser(
      @PathVariable UUID userId, @PathVariable String businessClientId) {
    adminSecurityService.unassignClientScopeFromUser(userId, businessClientId);
  }

  private AdminDtos.RoleResponse toRoleResponse(com.gurch.sandbox.auth.internal.RoleEntity role) {
    return new AdminDtos.RoleResponse(
        role.id(), role.code(), role.name(), role.createdAt(), role.updatedAt());
  }

  private AdminDtos.PermissionResponse toPermissionResponse(
      com.gurch.sandbox.auth.internal.PermissionEntity permission) {
    return new AdminDtos.PermissionResponse(
        permission.id(),
        permission.code(),
        permission.description(),
        permission.createdAt(),
        permission.updatedAt());
  }

  private AdminDtos.WorkflowGroupResponse toWorkflowGroupResponse(
      com.gurch.sandbox.auth.internal.WorkflowGroupEntity workflowGroup) {
    return new AdminDtos.WorkflowGroupResponse(
        workflowGroup.id(),
        workflowGroup.code(),
        workflowGroup.name(),
        workflowGroup.createdAt(),
        workflowGroup.updatedAt());
  }
}
