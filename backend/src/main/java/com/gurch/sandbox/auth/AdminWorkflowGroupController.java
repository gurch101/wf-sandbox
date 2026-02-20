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
@RequestMapping("/api/admin/workflow-groups")
@Validated
@PreAuthorize("hasAuthority('admin.security.manage')")
public class AdminWorkflowGroupController {

  private final AdminSecurityService adminSecurityService;

  public AdminWorkflowGroupController(AdminSecurityService adminSecurityService) {
    this.adminSecurityService = adminSecurityService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AdminDtos.CreatedAdminEntityResponse createWorkflowGroup(
      @Valid @RequestBody AdminDtos.CreateWorkflowGroupRequest request) {
    return new AdminDtos.CreatedAdminEntityResponse(
        adminSecurityService.createWorkflowGroup(request.getCode(), request.getName()));
  }

  @GetMapping
  public AdminDtos.WorkflowGroupListResponse listWorkflowGroups(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size,
      @RequestParam(required = false) String codeContains) {
    return new AdminDtos.WorkflowGroupListResponse(
        adminSecurityService.listWorkflowGroups(page, size, codeContains).stream()
            .map(AdminDtoMapper::toWorkflowGroupResponse)
            .toList(),
        adminSecurityService.countWorkflowGroups(codeContains),
        page,
        size);
  }

  @GetMapping("/{workflowGroupCode}")
  public AdminDtos.WorkflowGroupResponse getWorkflowGroupByCode(
      @PathVariable String workflowGroupCode) {
    return AdminDtoMapper.toWorkflowGroupResponse(
        adminSecurityService.getWorkflowGroupByCode(workflowGroupCode));
  }

  @DeleteMapping("/{workflowGroupCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteWorkflowGroupByCode(@PathVariable String workflowGroupCode) {
    adminSecurityService.deleteWorkflowGroupByCode(workflowGroupCode);
  }

  @PutMapping("/{workflowGroupCode}")
  public AdminDtos.WorkflowGroupResponse updateWorkflowGroup(
      @PathVariable String workflowGroupCode,
      @Valid @RequestBody AdminDtos.UpdateWorkflowGroupRequest request) {
    return AdminDtoMapper.toWorkflowGroupResponse(
        adminSecurityService.updateWorkflowGroup(workflowGroupCode, request.getName()));
  }
}
