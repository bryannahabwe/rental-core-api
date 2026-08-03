package com.cognix.rentalcoreapi.shared.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Turns on the {@code @PreAuthorize} advisor for web slice tests, which would
 * otherwise get it only from {@code SecurityConfig} — and that drags in the JWT
 * filter and its repositories.
 *
 * <p>Deliberately a top-level class: a {@code @Configuration} nested inside the
 * test would be picked up as the test's <em>default configuration class</em>,
 * replacing {@code @SpringBootConfiguration} and leaving the slice with no
 * controllers mapped at all.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityTestConfig {
}
