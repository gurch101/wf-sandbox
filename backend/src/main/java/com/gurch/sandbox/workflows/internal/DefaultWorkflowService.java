package com.gurch.sandbox.workflows.internal;

import com.gurch.sandbox.workflows.WorkflowApi;
import com.gurch.sandbox.workflows.WorkflowDefinitionResponse;
import com.gurch.sandbox.workflows.WorkflowSearchCriteria;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultWorkflowService implements WorkflowApi {

  private final RepositoryService repositoryService;

  @Override
  public boolean processDefinitionExists(String processDefinitionKey) {
    return repositoryService
            .createProcessDefinitionQuery()
            .processDefinitionKey(processDefinitionKey)
            .latestVersion()
            .count()
        > 0;
  }

  @Override
  public List<WorkflowDefinitionResponse> searchProcessDefinitions(
      WorkflowSearchCriteria criteria) {
    var query = repositoryService.createProcessDefinitionQuery().latestVersion().active();
    if (criteria.getProcessDefinitionKeyPattern() != null) {
      query = query.processDefinitionKeyLike(criteria.getProcessDefinitionKeyPattern());
    }

    return query.list().stream()
        .map(
            pd ->
                WorkflowDefinitionResponse.builder()
                    .id(pd.getId())
                    .key(pd.getKey())
                    .name(pd.getName())
                    .version(pd.getVersion())
                    .build())
        .toList();
  }
}
