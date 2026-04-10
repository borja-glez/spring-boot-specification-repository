# Extensibility

The library is designed around extension contracts in the `com.borjaglez.specrepository` package:

- `OperatorHandler`: add or replace operators.
- `ValueConverter`: override value parsing for custom types.
- `SpecificationRepositoryCustomizer`: adjust the repository pipeline built by the Spring Boot starters.
- `SpecificationRepository`: use the fluent DSL or execute an externally built `QueryPlan`.

Example custom operator use cases:

- PostgreSQL JSONB comparisons
- case-insensitive locale-specific matching
- tenant-aware predicates

## Spring Boot starters

The Boot 3 and Boot 4 starters expose extensibility through beans:

- `OperatorHandler` beans are appended after the defaults, so custom handlers can replace the built-in operator for the same `FilterOperator`.
- `ValueConverter` beans are applied before the defaults, so domain-specific converters can override standard parsing.
- `SpecificationRepositoryCustomizer` beans can tweak the `SpecificationRepositoryConfiguration.Builder` before the final repository pipeline is created.
- Advanced scenarios can provide `PathResolver`, `ConversionService`, `QueryPlanSpecificationFactory`, or a full `SpecificationRepositoryConfiguration` bean.

## Field whitelisting

When the DSL is exposed through a public HTTP API, restrict which fields clients can filter and sort by using `AllowedFieldsPolicy`. The policy is applied per-query, so each endpoint can define its own restrictions.

```java
// Define a policy — only these fields are allowed
AllowedFieldsPolicy policy = AllowedFieldsPolicy.of(
    Set.of("name", "email", "status"),   // allowed for filtering
    Set.of("name", "createdAt"));        // allowed for sorting

// Apply to a query — disallowed fields throw DisallowedFieldException
List<User> users = userRepository.query()
    .allowedFields(policy)
    .where("name", Operators.CONTAINS, searchTerm)
    .sort(Sort.by("createdAt"))
    .findAll();
```

Without `allowedFields()`, all fields are permitted (backward-compatible default).

### Security example: REST controller

```java
@RestController
@RequestMapping("/api/users")
class UserController {
  private static final AllowedFieldsPolicy USER_POLICY = AllowedFieldsPolicy.of(
      Set.of("name", "email", "status", "createdAt"),
      Set.of("name", "createdAt"));

  @GetMapping
  Page<User> search(
      @RequestParam String field,
      @RequestParam String value,
      Pageable pageable) {
    return userRepository.query()
        .allowedFields(USER_POLICY)
        .where(field, Operators.EQUALS, value)
        .findAll(pageable);
  }
}
```

Attempting to filter by `passwordHash` or sort by `internalScore` throws `DisallowedFieldException` with a clear message: `"Field 'passwordHash' is not allowed for filtering"`.
