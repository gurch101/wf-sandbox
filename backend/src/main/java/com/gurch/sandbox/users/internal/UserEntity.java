package com.gurch.sandbox.users.internal;

import com.gurch.sandbox.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@Table("users")
public class UserEntity extends BaseEntity<Long> {
  private String username;
  private String displayName;
  private boolean active;
}
