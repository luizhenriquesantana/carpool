---
description: 'Guidelines for building Spring Boot base applications'
applyTo: '**/*.java, **/*.kt'
version: '1'
disclaimer: 'This file provides general best practices.'
---

# Spring Boot Development

## General Instructions

- Make only high confidence suggestions when reviewing code changes.
- Write code with good maintainability practices, including comments on why certain design decisions were made.
- Identify edge cases and prompt user before modifying to address them.
- Write clear exception handling for confirmed edge cases.
- For libraries or external dependencies, mention their usage and purpose in comments.
- For Kotlin projects, use idiomatic Kotlin features and syntax.

## Spring Boot Instructions

### Dependency Injection

- Use constructor injection for all required dependencies.
- Declare dependency fields as `private final` (Java) or `val` (Kotlin).

### Configuration

- Use externalized configuration files (`application.yml` or `application.properties`) as per the repository's convention, or follow team's external configuration repository practices.
- Environment Profiles: Use Spring profiles for different environments (dev, test, prod).
- Configuration Properties: Use `@ConfigurationProperties` for type-safe configuration binding.
- Secrets Management: Externalize secrets using environment variables or secret management systems.

### Code Organization

- Package Structure: Organize by feature/domain rather than by layer.
- Separation of Concerns: Keep controllers thin, services focused, and repositories simple.
- Utility Classes: Make utility classes final with private constructors.

### Code Quality
- Keep methods focused and reasonably sized.
- Use descriptive variable and method names.
- Avoid deep nesting (generally no more than 3-4 levels).
- Use meaningful exception messages.
- Avoid magic numbers; use named constants or configuration properties.

### Service Layer

- Place business logic in `@Service`-annotated classes.
- Services should be stateless and testable.
- Inject repositories via the constructor.
- Service method signatures should use domain IDs or DTOs, not expose repository entities directly unless necessary.

### Logging

- Use SLF4J for all logging.
- Do not use concrete implementations (Logback, Log4j2) or `System.out.println()` directly.
- Use parameterized logging.
- Never log sensitive information (passwords, tokens, PII).

### Security & Input Handling

- Use parameterized queries | Always use Spring Data JPA or `NamedParameterJdbcTemplate` to prevent SQL injection.
- Validate request bodies and parameters using JSR-380 annotations and `BindingResult`.
- Use Spring Security for authentication and authorization if present.
- Validate all inputs at API boundaries using appropriate validation annotations.
- Use Spring Security annotations for endpoint protection when security is enabled.

### Build and Verification

- After adding or modifying code, verify the project continues to build successfully.
- Use the build tool specified in the repository (Maven, Gradle, or other).
- Ensure all tests pass as part of the build without removing or modifying existing application code.
- Write unit and integration tests using the framework specified in the repository.
- Ensure tests are deterministic and do not depend on external state.
- Maintain minimum code coverage thresholds (commonly 80% instruction coverage).
- Generate and review coverage reports as part of the build process.

### Performance
- Avoid N+1 queries in database operations.
- Use appropriate Spring caching annotations (`@Cacheable`, `@CacheEvict`) when beneficial.
- Close resources properly using try-with-resources.
- Avoid unnecessary object creation in frequently executed code paths.

### Documentation

- Document public APIs and important business logic with Javadoc/KDoc comments.
