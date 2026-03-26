package com.gurch.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class OpenApiDocumentationTest {

  @Test
  void controllerSignatureDtosShouldBeDocumentedWithSchema() {
    Set<Class<?>> undocumentedDtos = new LinkedHashSet<>();

    for (Class<?> controllerClass : findControllerClasses()) {
      for (Method method : controllerClass.getDeclaredMethods()) {
        collectSignatureTypes(method.getGenericReturnType()).stream()
            .filter(OpenApiDocumentationTest::shouldRequireSchema)
            .filter(type -> !type.isAnnotationPresent(Schema.class))
            .forEach(undocumentedDtos::add);

        for (Type parameterType : method.getGenericParameterTypes()) {
          collectSignatureTypes(parameterType).stream()
              .filter(OpenApiDocumentationTest::shouldRequireSchema)
              .filter(type -> !type.isAnnotationPresent(Schema.class))
              .forEach(undocumentedDtos::add);
        }
      }
    }

    assertThat(undocumentedDtos)
        .as("Controller request/response DTOs should be annotated with @Schema")
        .isEmpty();
  }

  private static Set<Class<?>> collectSignatureTypes(Type type) {
    Set<Class<?>> collected = new LinkedHashSet<>();
    collectSignatureTypes(type, collected);
    return collected;
  }

  private static void collectSignatureTypes(Type type, Set<Class<?>> collected) {
    if (type instanceof Class<?> clazz) {
      collected.add(clazz);
      return;
    }
    if (type instanceof ParameterizedType parameterizedType) {
      collectSignatureTypes(parameterizedType.getRawType(), collected);
      for (Type actualTypeArgument : parameterizedType.getActualTypeArguments()) {
        collectSignatureTypes(actualTypeArgument, collected);
      }
      return;
    }
    if (type instanceof WildcardType wildcardType) {
      for (Type upperBound : wildcardType.getUpperBounds()) {
        collectSignatureTypes(upperBound, collected);
      }
    }
  }

  private static boolean shouldRequireSchema(Class<?> type) {
    return !type.isPrimitive()
        && !type.isInterface()
        && !type.isEnum()
        && !Throwable.class.isAssignableFrom(type)
        && !type.isAnnotationPresent(RestController.class)
        && !MultipartFile.class.isAssignableFrom(type)
        && !StreamingResponseBody.class.isAssignableFrom(type)
        && !type.getPackageName().startsWith("java.")
        && !type.getPackageName().startsWith("org.springframework.");
  }

  private static Set<Class<?>> findControllerClasses() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

    Set<Class<?>> controllers = new LinkedHashSet<>();
    for (var candidate : scanner.findCandidateComponents("com.gurch.sandbox")) {
      try {
        controllers.add(Class.forName(candidate.getBeanClassName()));
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException("Could not load controller class", e);
      }
    }
    return controllers;
  }
}
