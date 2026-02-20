package com.gurch.sandbox.auth;

import com.gurch.sandbox.auth.internal.AdminSecurityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/admin/roles")
@Validated
@PreAuthorize("hasAuthority('admin.security.manage')")
public class AdminRoleController {

  private final AdminSecurityService adminSecurityService;

  public AdminRoleController(AdminSecurityService adminSecurityService) {
    this.adminSecurityService = adminSecurityService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AdminDtos.CreatedAdminEntityResponse createRole(
      @Valid @RequestBody AdminDtos.CreateRoleRequest request) {
    return new AdminDtos.CreatedAdminEntityResponse(
        adminSecurityService.createRole(request.getCode(), request.getName()));
  }

  @GetMapping
  public AdminDtos.RoleListResponse listRoles(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size,
      @RequestParam(required = false) String codeContains) {
    return new AdminDtos.RoleListResponse(
        adminSecurityService.listRoles(page, size, codeContains).stream()
            .map(AdminDtoMapper::toRoleResponse)
            .toList(),
        adminSecurityService.countRoles(codeContains),
        page,
        size);
  }

  @GetMapping("/{roleCode}")
  public AdminDtos.RoleResponse getRoleByCode(@PathVariable String roleCode) {
    return AdminDtoMapper.toRoleResponse(adminSecurityService.getRoleByCode(roleCode));
  }

  @DeleteMapping("/{roleCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteRoleByCode(@PathVariable String roleCode) {
    adminSecurityService.deleteRoleByCode(roleCode);
  }

  @PutMapping("/{roleCode}")
  public AdminDtos.RoleResponse updateRole(
      @PathVariable String roleCode, @Valid @RequestBody AdminDtos.UpdateRoleRequest request) {
    return AdminDtoMapper.toRoleResponse(
        adminSecurityService.updateRole(roleCode, request.getName()));
  }

  @PutMapping("/{roleCode}/permissions/{permissionCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void assignPermissionToRole(
      @PathVariable String roleCode, @PathVariable String permissionCode) {
    adminSecurityService.assignPermissionToRole(roleCode, permissionCode);
  }

  @GetMapping("/{roleCode}/permissions")
  public AdminDtos.RolePermissionAssignmentListResponse listPermissionsAssignedToRole(
      @PathVariable String roleCode) {
    return new AdminDtos.RolePermissionAssignmentListResponse(
        adminSecurityService.listPermissionAssignmentsForRole(roleCode));
  }

  @DeleteMapping("/{roleCode}/permissions/{permissionCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unassignPermissionFromRole(
      @PathVariable String roleCode, @PathVariable String permissionCode) {
    adminSecurityService.unassignPermissionFromRole(roleCode, permissionCode);
  }
}
