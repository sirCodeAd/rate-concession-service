package com.mortgage.rate_concession_service.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method parameter to be resolved to the validated {@link
 * com.mortgage.rate_concession_service.domain.AppUser} for the current request, as populated by
 * {@link AuthenticationInterceptor}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentUser {
}
