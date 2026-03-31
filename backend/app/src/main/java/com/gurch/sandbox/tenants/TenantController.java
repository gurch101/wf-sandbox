package com.gurch.sandbox.tenants;

import com.gurch.sandbox.dto.CreateResponse;
import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.tenants.dto.TenantCommand;
import com.gurch.sandbox.tenants.dto.TenantDtos;
import com.gurch.sandbox.tenants.dto.TenantResponse;
import com.gurch.sandbox.tenants.dto.TenantSearchCriteria;
import com.gurch.sandbox.tenants.dto.TenantSearchResponse;
import com.gurch.sandbox.web.NotFoundException;
import com.gurch.sandbox.web.ValidationErrorEnum;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
public class TenantController {

  private final TenantApi tenantApi;

  @PostMapping
  @ValidationErrorEnum({TenantValidationErrorCode.class})
  @ResponseStatus(HttpStatus.CREATED)
  public CreateResponse create(@Valid @RequestBody TenantDtos.CreateTenantRequest request) {
    return new CreateResponse(
        tenantApi
            .create(
                TenantCommand.builder().name(request.getName()).active(request.getActive()).build())
            .longValue());
  }

  @GetMapping("/{id}")
  public TenantResponse getById(@PathVariable Integer id) {
    return tenantApi.findById(id).orElseThrow(() -> new NotFoundException("Tenant not found"));
  }

  @PutMapping("/{id}")
  @ValidationErrorEnum({TenantValidationErrorCode.class})
  public CreateResponse update(
      @PathVariable Integer id, @Valid @RequestBody TenantDtos.UpdateTenantRequest request) {
    return new CreateResponse(
        tenantApi
            .update(
                id,
                TenantCommand.builder().name(request.getName()).active(request.getActive()).build(),
                request.getVersion())
            .longValue());
  }

  @DeleteMapping("/{id}")
  @ValidationErrorEnum({TenantValidationErrorCode.class})
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Integer id) {
    tenantApi.deleteById(id);
  }

  @GetMapping("/search")
  public PagedResponse<TenantSearchResponse> search(TenantSearchCriteria criteria) {
    return tenantApi.search(criteria);
  }
}
