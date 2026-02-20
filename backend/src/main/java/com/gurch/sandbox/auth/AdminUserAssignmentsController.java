package com.gurch.sandbox.auth;

import com.gurch.sandbox.auth.internal.AdminSecurityService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@Validated
@PreAuthorize("hasAuthority('admin.security.manage')")
public class AdminUserAssignmentsController {

  private final AdminSecurityService adminSecurityService;

  public AdminUserAssignmentsController(AdminSecurityService adminSecurityService) {
    this.adminSecurityService = adminSecurityService;
  }

  @PutMapping("/{userId}/roles/{roleCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void assignRoleToUser(@PathVariable UUID userId, @PathVariable String roleCode) {
    adminSecurityService.assignRoleToUser(userId, roleCode);
  }

  @GetMapping("/{userId}/roles")
  public AdminDtos.UserRoleAssignmentListResponse listRolesAssignedToUser(
      @PathVariable UUID userId) {
    return new AdminDtos.UserRoleAssignmentListResponse(
        adminSecurityService.listRoleAssignmentsForUser(userId));
  }

  @DeleteMapping("/{userId}/roles/{roleCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unassignRoleFromUser(@PathVariable UUID userId, @PathVariable String roleCode) {
    adminSecurityService.unassignRoleFromUser(userId, roleCode);
  }

  @PutMapping("/{userId}/workflow-groups/{workflowGroupCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void assignWorkflowGroupToUser(
      @PathVariable UUID userId, @PathVariable String workflowGroupCode) {
    adminSecurityService.assignWorkflowGroupToUser(userId, workflowGroupCode);
  }

  @GetMapping("/{userId}/workflow-groups")
  public AdminDtos.UserWorkflowGroupAssignmentListResponse listWorkflowGroupsAssignedToUser(
      @PathVariable UUID userId) {
    return new AdminDtos.UserWorkflowGroupAssignmentListResponse(
        adminSecurityService.listWorkflowGroupAssignmentsForUser(userId));
  }

  @DeleteMapping("/{userId}/workflow-groups/{workflowGroupCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unassignWorkflowGroupFromUser(
      @PathVariable UUID userId, @PathVariable String workflowGroupCode) {
    adminSecurityService.unassignWorkflowGroupFromUser(userId, workflowGroupCode);
  }

  @PutMapping("/{userId}/client-scopes/{businessClientId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void assignClientScopeToUser(
      @PathVariable UUID userId, @PathVariable String businessClientId) {
    adminSecurityService.assignClientScopeToUser(userId, businessClientId);
  }

  @GetMapping("/{userId}/client-scopes")
  public AdminDtos.UserClientScopeAssignmentListResponse listClientScopesAssignedToUser(
      @PathVariable UUID userId) {
    return new AdminDtos.UserClientScopeAssignmentListResponse(
        adminSecurityService.listClientScopeAssignmentsForUser(userId));
  }

  @DeleteMapping("/{userId}/client-scopes/{businessClientId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unassignClientScopeFromUser(
      @PathVariable UUID userId, @PathVariable String businessClientId) {
    adminSecurityService.unassignClientScopeFromUser(userId, businessClientId);
  }
}
