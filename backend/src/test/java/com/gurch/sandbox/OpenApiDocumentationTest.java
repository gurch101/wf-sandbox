package com.gurch.sandbox;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class OpenApiDocumentationTest {

  @Test
  void endpointParamsAndReturnTypesShouldBeDocumentedWithSchema() throws Exception {
    Set<Class<?>> endpointTypes = collectEndpointModelTypes();
    List<String> missingSchema = new ArrayList<>();
    for (Class<?> type : endpointTypes) {
      if (type.getAnnotation(Schema.class) == null) {
        missingSchema.add(type.getName());
      }
    }

    assertTrue(
        missingSchema.isEmpty(),
        () ->
            "Request/response model types used by mapped endpoints must be annotated with @Schema:"
                + System.lineSeparator()
                + String.join(System.lineSeparator(), missingSchema));
  }

  private Set<Class<?>> collectEndpointModelTypes() throws Exception {
    Set<Class<?>> results = new LinkedHashSet<>();
    for (Class<?> controller : findRestControllers()) {
      for (Method method : controller.getDeclaredMethods()) {
        if (method.isBridge() || method.isSynthetic()) {
          continue;
        }
        if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
          continue;
        }
        collectModelTypes(method.getGenericReturnType(), results);
        for (Type parameterType : method.getGenericParameterTypes()) {
          collectModelTypes(parameterType, results);
        }
      }
    }
    return results;
  }

  private Set<Class<?>> findRestControllers() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    Set<Class<?>> controllers = new HashSet<>();
    for (BeanDefinition beanDefinition : scanner.findCandidateComponents("com.gurch.sandbox")) {
      controllers.add(Class.forName(beanDefinition.getBeanClassName()));
    }
    return controllers;
  }

  private void collectModelTypes(Type type, Set<Class<?>> collector) {
    if (type instanceof Class<?> clazz) {
      if (shouldCheck(clazz)) {
        collector.add(clazz);
      }
      if (clazz.isArray()) {
        collectModelTypes(clazz.getComponentType(), collector);
      }
      return;
    }

    if (type instanceof ParameterizedType parameterizedType) {
      collectModelTypes(parameterizedType.getRawType(), collector);
      for (Type argument : parameterizedType.getActualTypeArguments()) {
        collectModelTypes(argument, collector);
      }
      return;
    }

    if (type instanceof WildcardType wildcardType) {
      for (Type upperBound : wildcardType.getUpperBounds()) {
        collectModelTypes(upperBound, collector);
      }
      for (Type lowerBound : wildcardType.getLowerBounds()) {
        collectModelTypes(lowerBound, collector);
      }
      return;
    }

    if (type instanceof GenericArrayType genericArrayType) {
      collectModelTypes(genericArrayType.getGenericComponentType(), collector);
    }
  }

  private boolean shouldCheck(Class<?> type) {
    String packageName = type.getPackageName();
    return packageName.startsWith("com.gurch.sandbox")
        && !packageName.contains(".internal")
        && !type.isAnnotation()
        && !type.isEnum()
        && !type.isInterface()
        && !type.isPrimitive()
        && type != Void.TYPE
        && !type.isAnnotationPresent(RestController.class);
  }
}
