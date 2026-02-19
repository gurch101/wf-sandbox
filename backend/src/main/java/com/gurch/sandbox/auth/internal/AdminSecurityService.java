package com.gurch.sandbox.auth.internal;

import com.gurch.sandbox.web.ConflictException;
import com.gurch.sandbox.web.NotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminSecurityService {

  private final AdminSecurityRepository repository;

  public AdminSecurityService(AdminSecurityRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public UUID createRole(String code, String name) {
    try {
      return repository.createRole(code, name);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("Role already exists for code: " + code);
    }
  }

  @Transactional
  public UUID createPermission(String code, String description) {
    try {
      return repository.createPermission(code, description);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("Permission already exists for code: " + code);
    }
  }

  @Transactional
  public UUID createWorkflowGroup(String code, String name) {
    try {
      return repository.createWorkflowGroup(code, name);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("Workflow group already exists for code: " + code);
    }
  }

  @Transactional(readOnly = true)
  public RoleEntity getRoleByCode(String roleCode) {
    return repository
        .findRoleByCode(roleCode)
        .orElseThrow(() -> new NotFoundException("Role not found for code: " + roleCode));
  }

  @Transactional(readOnly = true)
  public List<RoleEntity> listRoles(int page, int size, String codeContains) {
    String codePattern = normalizeCodePattern(codeContains);
    return repository.findRoles(codePattern, page, size);
  }

  @Transactional(readOnly = true)
  public long countRoles(String codeContains) {
    return repository.countRoles(normalizeCodePattern(codeContains));
  }

  @Transactional(readOnly = true)
  public PermissionEntity getPermissionByCode(String permissionCode) {
    return repository
        .findPermissionByCode(permissionCode)
        .orElseThrow(
            () -> new NotFoundException("Permission not found for code: " + permissionCode));
  }

  @Transactional(readOnly = true)
  public List<PermissionEntity> listPermissions(int page, int size, String codeContains) {
    return repository.findPermissions(normalizeCodePattern(codeContains), page, size);
  }

  @Transactional(readOnly = true)
  public long countPermissions(String codeContains) {
    return repository.countPermissions(normalizeCodePattern(codeContains));
  }

  @Transactional
  public void deletePermissionByCode(String permissionCode) {
    UUID permissionId =
        repository
            .findPermissionIdByCode(permissionCode)
            .orElseThrow(
                () -> new NotFoundException("Permission not found for code: " + permissionCode));
    if (repository.countPermissionRoleAssignments(permissionId) > 0) {
      throw new ConflictException("Permission is in use and cannot be deleted: " + permissionCode);
    }
    repository.deletePermissionByCode(permissionCode);
  }

  @Transactional(readOnly = true)
  public WorkflowGroupEntity getWorkflowGroupByCode(String workflowGroupCode) {
    return repository
        .findWorkflowGroupByCode(workflowGroupCode)
        .orElseThrow(
            () -> new NotFoundException("Workflow group not found for code: " + workflowGroupCode));
  }

  @Transactional(readOnly = true)
  public List<WorkflowGroupEntity> listWorkflowGroups(int page, int size, String codeContains) {
    return repository.findWorkflowGroups(normalizeCodePattern(codeContains), page, size);
  }

  @Transactional(readOnly = true)
  public long countWorkflowGroups(String codeContains) {
    return repository.countWorkflowGroups(normalizeCodePattern(codeContains));
  }

  @Transactional
  public void deleteWorkflowGroupByCode(String workflowGroupCode) {
    UUID workflowGroupId =
        repository
            .findWorkflowGroupIdByCode(workflowGroupCode)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Workflow group not found for code: " + workflowGroupCode));
    if (repository.countWorkflowGroupUserAssignments(workflowGroupId) > 0
        || repository.countRequestWorkflowGroupReferences(workflowGroupCode) > 0) {
      throw new ConflictException(
          "Workflow group is in use and cannot be deleted: " + workflowGroupCode);
    }
    repository.deleteWorkflowGroupByCode(workflowGroupCode);
  }

  @Transactional
  public void deleteRoleByCode(String roleCode) {
    UUID roleId =
        repository
            .findRoleIdByCode(roleCode)
            .orElseThrow(() -> new NotFoundException("Role not found for code: " + roleCode));
    if (repository.countUserRoleAssignments(roleId) > 0
        || repository.countRolePermissionAssignments(roleId) > 0) {
      throw new ConflictException("Role is in use and cannot be deleted: " + roleCode);
    }
    repository.deleteRoleByCode(roleCode);
  }

  @Transactional
  public RoleEntity updateRole(String roleCode, String name) {
    int updated = repository.updateRoleNameByCode(roleCode, name);
    if (updated == 0) {
      throw new NotFoundException("Role not found for code: " + roleCode);
    }
    return getRoleByCode(roleCode);
  }

  @Transactional
  public void assignPermissionToRole(String roleCode, String permissionCode) {
    UUID roleId =
        repository
            .findRoleIdByCode(roleCode)
            .orElseThrow(() -> new NotFoundException("Role not found for code: " + roleCode));
    UUID permissionId =
        repository
            .findPermissionIdByCode(permissionCode)
            .orElseThrow(
                () -> new NotFoundException("Permission not found for code: " + permissionCode));
    repository.linkRolePermission(roleId, permissionId);
  }

  @Transactional
  public PermissionEntity updatePermission(String permissionCode, String description) {
    int updated = repository.updatePermissionDescriptionByCode(permissionCode, description);
    if (updated == 0) {
      throw new NotFoundException("Permission not found for code: " + permissionCode);
    }
    return getPermissionByCode(permissionCode);
  }

  @Transactional(readOnly = true)
  public List<String> listPermissionAssignmentsForRole(String roleCode) {
    UUID roleId =
        repository
            .findRoleIdByCode(roleCode)
            .orElseThrow(() -> new NotFoundException("Role not found for code: " + roleCode));
    return repository.findPermissionCodesByRoleId(roleId);
  }

  @Transactional
  public void unassignPermissionFromRole(String roleCode, String permissionCode) {
    UUID roleId =
        repository
            .findRoleIdByCode(roleCode)
            .orElseThrow(() -> new NotFoundException("Role not found for code: " + roleCode));
    UUID permissionId =
        repository
            .findPermissionIdByCode(permissionCode)
            .orElseThrow(
                () -> new NotFoundException("Permission not found for code: " + permissionCode));
    repository.unlinkRolePermission(roleId, permissionId);
  }

  @Transactional
  public void assignRoleToUser(UUID userId, String roleCode) {
    UUID existingUserId =
        repository
            .findUserId(userId)
            .orElseThrow(() -> new NotFoundException("User not found for id: " + userId));
    UUID roleId =
        repository
            .findRoleIdByCode(roleCode)
            .orElseThrow(() -> new NotFoundException("Role not found for code: " + roleCode));
    repository.linkUserRole(existingUserId, roleId);
  }

  @Transactional(readOnly = true)
  public List<String> listRoleAssignmentsForUser(UUID userId) {
    UUID existingUserId =
        repository
            .findUserId(userId)
            .orElseThrow(() -> new NotFoundException("User not found for id: " + userId));
    return repository.findRoleCodesByUserId(existingUserId);
  }

  @Transactional
  public void unassignRoleFromUser(UUID userId, String roleCode) {
    UUID existingUserId =
        repository
            .findUserId(userId)
            .orElseThrow(() -> new NotFoundException("User not found for id: " + userId));
    UUID roleId =
        repository
            .findRoleIdByCode(roleCode)
            .orElseThrow(() -> new NotFoundException("Role not found for code: " + roleCode));
    repository.unlinkUserRole(existingUserId, roleId);
  }

  @Transactional
  public void assignWorkflowGroupToUser(UUID userId, String workflowGroupCode) {
    UUID existingUserId =
        repository
            .findUserId(userId)
            .orElseThrow(() -> new NotFoundException("User not found for id: " + userId));
    UUID workflowGroupId =
        repository
            .findWorkflowGroupIdByCode(workflowGroupCode)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Workflow group not found for code: " + workflowGroupCode));
    repository.linkUserWorkflowGroup(existingUserId, workflowGroupId);
  }

  @Transactional
  public WorkflowGroupEntity updateWorkflowGroup(String workflowGroupCode, String name) {
    int updated = repository.updateWorkflowGroupNameByCode(workflowGroupCode, name);
    if (updated == 0) {
      throw new NotFoundException("Workflow group not found for code: " + workflowGroupCode);
    }
    return getWorkflowGroupByCode(workflowGroupCode);
  }

  @Transactional(readOnly = true)
  public List<String> listWorkflowGroupAssignmentsForUser(UUID userId) {
    UUID existingUserId =
        repository
            .findUserId(userId)
            .orElseThrow(() -> new NotFoundException("User not found for id: " + userId));
    return repository.findWorkflowGroupCodesByUserId(existingUserId);
  }

  @Transactional
  public void unassignWorkflowGroupFromUser(UUID userId, String workflowGroupCode) {
    UUID existingUserId =
        repository
            .findUserId(userId)
            .orElseThrow(() -> new NotFoundException("User not found for id: " + userId));
    UUID workflowGroupId =
        repository
            .findWorkflowGroupIdByCode(workflowGroupCode)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Workflow group not found for code: " + workflowGroupCode));
    repository.unlinkUserWorkflowGroup(existingUserId, workflowGroupId);
  }

  @Transactional
  public void assignClientScopeToUser(UUID userId, String businessClientId) {
    UUID existingUserId =
        repository
            .findUserId(userId)
            .orElseThrow(() -> new NotFoundException("User not found for id: " + userId));
    repository.linkPrincipalClientScope(existingUserId, businessClientId);
  }

  @Transactional(readOnly = true)
  public List<String> listClientScopeAssignmentsForUser(UUID userId) {
    UUID existingUserId =
        repository
            .findUserId(userId)
            .orElseThrow(() -> new NotFoundException("User not found for id: " + userId));
    return repository.findClientScopeIdsByUserId(existingUserId);
  }

  @Transactional
  public void unassignClientScopeFromUser(UUID userId, String businessClientId) {
    UUID existingUserId =
        repository
            .findUserId(userId)
            .orElseThrow(() -> new NotFoundException("User not found for id: " + userId));
    repository.unlinkPrincipalClientScope(existingUserId, businessClientId);
  }

  private String normalizeCodePattern(String codeContains) {
    if (codeContains == null || codeContains.isBlank()) {
      return null;
    }
    return "%" + codeContains.trim().toUpperCase(Locale.ROOT) + "%";
  }
}
