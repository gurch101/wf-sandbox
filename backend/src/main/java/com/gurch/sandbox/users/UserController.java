package com.gurch.sandbox.users;

import com.gurch.sandbox.dto.CreateResponse;
import com.gurch.sandbox.web.ApiErrorEnum;
import com.gurch.sandbox.web.NotFoundException;
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
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserController {

  private final UserApi userApi;

  @PostMapping
  @ApiErrorEnum({UserErrorCode.class})
  @ResponseStatus(HttpStatus.CREATED)
  public CreateResponse create(@Valid @RequestBody UserDtos.CreateUserRequest request) {
    return new CreateResponse(
        userApi
            .create(
                UserCommand.builder()
                    .username(request.getUsername())
                    .email(request.getEmail())
                    .active(request.getActive())
                    .tenantId(request.getTenantId())
                    .build())
            .longValue());
  }

  @GetMapping("/{id}")
  public UserResponse getById(@PathVariable Integer id) {
    return userApi.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
  }

  @PutMapping("/{id}")
  @ApiErrorEnum({UserErrorCode.class})
  public CreateResponse update(
      @PathVariable Integer id, @Valid @RequestBody UserDtos.UpdateUserRequest request) {
    return new CreateResponse(
        userApi
            .update(
                id,
                UserCommand.builder()
                    .email(request.getEmail())
                    .active(request.getActive())
                    .tenantId(request.getTenantId())
                    .build(),
                request.getVersion())
            .longValue());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Integer id) {
    userApi.deleteById(id);
  }

  @GetMapping("/search")
  public UserDtos.SearchResponse search(UserSearchCriteria criteria) {
    return new UserDtos.SearchResponse(userApi.search(criteria));
  }
}
