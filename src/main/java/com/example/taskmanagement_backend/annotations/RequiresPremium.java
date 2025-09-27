package com.example.taskmanagement_backend.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark endpoints that require premium subscription
 * Implements graceful degradation instead of hard blocking
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPremium {

    /**
     * Custom message to show when subscription is expired
     */
    String message() default "Upgrade to Premium to continue using this feature";

    /**
     * Feature name for analytics tracking
     */
    String feature() default "";

    /**
     * Whether to allow read-only access for expired users
     */
    boolean allowReadOnly() default false;
}
