package com.gurch.sandbox;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(
    packages = "com.gurch.sandbox",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class OpenApiDocumentationTest {

  @ArchTest
  static final ArchRule DTOS_SHOULD_BE_DOCUMENTED_WITH_SCHEMA =
      classes()
          .that(isADtoUsedInController())
          .should()
          .beAnnotatedWith(Schema.class)
          .as("Request and Response DTOs used in RestControllers should be annotated with @Schema");

  private static DescribedPredicate<JavaClass> isADtoUsedInController() {
    return new DescribedPredicate<>("is a DTO used in a RestController") {
      @Override
      public boolean test(JavaClass javaClass) {
        if (javaClass.isPrimitive()
            || javaClass.isInterface()
            || javaClass.isEnum()
            || javaClass.getPackageName().startsWith("java.")
            || javaClass.getPackageName().startsWith("org.springframework.")
            || javaClass.isAnnotatedWith(RestController.class)
            || javaClass.isAnnotatedWith("org.springframework.data.relational.core.mapping.Table")
            || javaClass.getSimpleName().endsWith("Service")
            || javaClass.getSimpleName().endsWith("Repository")
            || javaClass.getSimpleName().endsWith("Exception")
            || javaClass.getSimpleName().contains("Builder")) {
          return false;
        }

        for (Dependency dependency : javaClass.getDirectDependenciesToSelf()) {
          if (dependency.getOriginClass().isAnnotatedWith(RestController.class)) {
            return true;
          }
        }

        return false;
      }
    };
  }
}
