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

The extension contracts in `jpa.spi` and `SpecificationRepositoryConfiguration` are `@API(status = MAINTAINED)`. `PathResolver`, `QueryPlanSpecificationFactory` and the rest of `jpa.support` are `@API(status = INTERNAL)`: replacing them works, but their shape may change in any release. See [API stability levels](architecture.md#api-stability-levels).

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

The policy guards client input: the plan's client conditions (`rootCondition()`) and its sort. A
`having` is not checked: the HTTP syntax has none, so it is server input. Conditions the server adds by deriving a plan (`plan.toBuilder()` or
`repository.query(plan)`) go to `serverCondition()`, are ANDed with the client conditions and are
not checked, so they may use fields the client cannot filter by:

```java
List<Order> mine = orderRepository.query(planFromClient)
    .where("customerId", Operators.EQUALS, currentUser.id())   // not in the whitelist
    .findAll();
```

A sort set with `sort(...)` or `sortedByDefault(...)` on a derived builder is server input too and
is not checked; the client sort the derived plan keeps and the sort of a sorted `Pageable` still
are.

`AllowedFieldsPolicy.validate(plan)` runs the same check on demand; the HTTP argument resolver
calls it so that a disallowed client field fails before the controller runs.

Selections, `groupBy`, aggregates, joins, fetches and subquery bodies are defined by the server and
are not checked against the policy. Never pass a client value straight to `select(...)`,
`groupBy(...)` or `leftFetch(...)`; validate it yourself first. The README table
[What the policy covers](../README.md#what-the-policy-covers) lists every part of a plan and whether
it is checked.

### `@FilterableQuery` denies by default

For HTTP endpoints, `@FilterableQuery` builds the policy from its `filterableFields` and
`sortableFields`. An empty list allows nothing for its usage:

- no lists: no filtering and no sorting;
- only one list: the other usage stays denied;
- `allowAllFields = true`: every field, as `AllowedFieldsPolicy.allowAll()` (cannot be combined
  with the lists).

A request that uses an undeclared usage is rejected before the controller runs with
`UndeclaredFieldListException` (a `DisallowedFieldException` annotated with
`@ResponseStatus(BAD_REQUEST)`), whose message names the missing attribute, for example
`Field 'name' is not allowed for filtering: @FilterableQuery declares no filterableFields. Declare
filterableFields, or set allowAllFields = true to allow every field.`

Up to 0.3.x a parameter without lists allowed every field. To migrate, declare the lists (preferred)
or add `allowAllFields = true`.

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
