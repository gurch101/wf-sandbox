package com.gurch.sandbox.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares error-code enums that a controller method may return so they can be documented in
 * OpenAPI.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrorEnum {
  /**
   * @return error-code enum types that may be returned by this endpoint
   */
  Class<? extends ApiErrorCode>[] value();
}
