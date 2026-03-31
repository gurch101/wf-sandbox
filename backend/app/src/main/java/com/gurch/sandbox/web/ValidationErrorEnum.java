package com.gurch.sandbox.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares validation-error enums that a controller method may return for OpenAPI docs. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidationErrorEnum {
  /** Returns validation-error enum types that may be returned by this endpoint. */
  Class<? extends ValidationErrorCode>[] value();
}
