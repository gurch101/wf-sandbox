package com.gurch.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModuleStructureTest {

  private final ApplicationModules modules = ApplicationModules.of(SandboxApplication.class);

  @Test
  void verifyModuleStructure() {
    modules.verify();
  }

  @Test
  void writeDocumentationSnippets() {

    new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
  }

  @Test
  void tableAnnotatedClassesLiveOnlyInInternalModelsPackages() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Table.class));

    List<String> tableAnnotatedClasses =
        scanner.findCandidateComponents("com.gurch.sandbox").stream()
            .map(candidate -> candidate.getBeanClassName())
            .sorted()
            .toList();

    assertThat(tableAnnotatedClasses)
        .isNotEmpty()
        .allMatch(name -> name.contains(".internal.models."));
  }
}
