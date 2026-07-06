package com.example.template.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Meta-annotation that restricts a handler method to callers with the USER role.
 * A readable alias for {@code @PreAuthorize("hasRole('USER')")}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@PreAuthorize("hasRole('USER')")
public @interface UserOnly {
}
