package com.gurch.sandbox;

import org.junit.jupiter.api.Test;
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
}
