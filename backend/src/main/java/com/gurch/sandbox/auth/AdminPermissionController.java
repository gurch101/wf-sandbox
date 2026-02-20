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
@RequestMapping("/api/admin/permissions")
@Validated
@PreAuthorize("hasAuthority('admin.security.manage')")
public class AdminPermissionController {

  private final AdminSecurityService adminSecurityService;

  public AdminPermissionController(AdminSecurityService adminSecurityService) {
    this.adminSecurityService = adminSecurityService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AdminDtos.CreatedAdminEntityResponse createPermission(
      @Valid @RequestBody AdminDtos.CreatePermissionRequest request) {
    return new AdminDtos.CreatedAdminEntityResponse(
        adminSecurityService.createPermission(request.getCode(), request.getDescription()));
  }

  @GetMapping
  public AdminDtos.PermissionListResponse listPermissions(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size,
      @RequestParam(required = false) String codeContains) {
    return new AdminDtos.PermissionListResponse(
        adminSecurityService.listPermissions(page, size, codeContains).stream()
            .map(AdminDtoMapper::toPermissionResponse)
            .toList(),
        adminSecurityService.countPermissions(codeContains),
        page,
        size);
  }

  @GetMapping("/{permissionCode}")
  public AdminDtos.PermissionResponse getPermissionByCode(@PathVariable String permissionCode) {
    return AdminDtoMapper.toPermissionResponse(
        adminSecurityService.getPermissionByCode(permissionCode));
  }

  @DeleteMapping("/{permissionCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletePermissionByCode(@PathVariable String permissionCode) {
    adminSecurityService.deletePermissionByCode(permissionCode);
  }

  @PutMapping("/{permissionCode}")
  public AdminDtos.PermissionResponse updatePermission(
      @PathVariable String permissionCode,
      @Valid @RequestBody AdminDtos.UpdatePermissionRequest request) {
    return AdminDtoMapper.toPermissionResponse(
        adminSecurityService.updatePermission(permissionCode, request.getDescription()));
  }
}
