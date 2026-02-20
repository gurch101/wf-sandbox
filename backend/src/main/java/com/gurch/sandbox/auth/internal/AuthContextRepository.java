package com.gurch.sandbox.auth.internal;

import com.gurch.sandbox.auth.AuthContextApi;
import com.gurch.sandbox.query.BuiltQuery;
import com.gurch.sandbox.query.JoinType;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthContextRepository implements AuthContextApi {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public AuthContextRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public List<String> findWorkflowGroupCodes(UUID userId) {
    BuiltQuery query =
        SQLQueryBuilder.select("wg.code")
            .from("user_workflow_groups", "uwg")
            .join(JoinType.INNER, "workflow_groups", "wg", "wg.id = uwg.workflow_group_id")
            .where("uwg.user_id", Operator.EQ, userId)
            .orderBy("+wg.code")
            .build();
    return jdbcTemplate.query(query.sql(), query.params(), (rs, rowNum) -> rs.getString("code"));
  }

  @Override
  public List<String> findClientScopeIds(UUID userId) {
    BuiltQuery query =
        SQLQueryBuilder.select("pcs.business_client_id")
            .from("principal_client_scopes", "pcs")
            .where("pcs.principal_user_id", Operator.EQ, userId)
            .orderBy("+pcs.business_client_id")
            .build();
    return jdbcTemplate.query(
        query.sql(), query.params(), (rs, rowNum) -> rs.getString("business_client_id"));
  }

  @Override
  public List<String> findPermissionCodes(UUID userId) {
    BuiltQuery query =
        SQLQueryBuilder.select("distinct p.code")
            .from("user_roles", "ur")
            .join(JoinType.INNER, "role_permissions", "rp", "rp.role_id = ur.role_id")
            .join(JoinType.INNER, "permissions", "p", "p.id = rp.permission_id")
            .where("ur.user_id", Operator.EQ, userId)
            .orderBy("+p.code")
            .build();
    return jdbcTemplate.query(query.sql(), query.params(), (rs, rowNum) -> rs.getString("code"));
  }
}
