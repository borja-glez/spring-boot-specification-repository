# Upgrading

This guide lists the changes that need action when moving from one minor version to the next. The
full list of changes of each release is in [CHANGELOG.md](../CHANGELOG.md), where breaking entries
are marked with **BREAKING:**.

## Upgrading from 0.4.x to 1.0.0

1.0.0 fixes the public API until 2.0.0. Every public type now declares how stable it is with
[`@API`](https://github.com/apiguardian-team/apiguardian) (see
[API stability levels](architecture.md#api-stability-levels)), and `QueryPlan` changes shape so that
later 1.x releases can add settings to it without breaking code.

### `QueryPlan` is a final class without a public constructor

Issue #121.

Up to 0.4.x `QueryPlan` was a `record` with 14 components and four public constructors. Adding a
component, as the lock did in 0.4.0, changed the canonical constructor and broke every caller. It is
now a `final` class:

- The accessors keep their names (`entityType()`, `rootCondition()`, `serverCondition()`, `joins()`,
  `fetches()`, `projections()`, `selections()`, `projectionType()`, `groupBy()`, `having()`, `sort()`,
  `distinct()`, `allowedFieldsPolicy()`, `lock()`), as do `toBuilder()`, `hasSelections()` and
  `hasAggregates()`. Code that reads a plan compiles unchanged, but must be recompiled: the type
  is no longer a record.
- `equals`, `hashCode` and `toString` still compare and print every component.
- The constructors are no longer public. A plan is created only through `SpecificationQueryBuilder`,
  `QueryPlanBuilder`, the fluent query of a repository, or `plan.toBuilder()` /
  `SpecificationQueryBuilder.from(plan)` to derive one from another plan.
- `QueryPlan` is no longer a `java.lang.Record`: record patterns (`case QueryPlan(var type, ...)`)
  and code that reflects on its record components no longer work. Use the accessors instead.

Before:

```java
QueryPlan<Order> plan = new QueryPlan<>(Order.class, rootCondition, List.of(), List.of(),
    List.of(), List.of(), null, List.of(), List.of(), Sort.by("placedAt"), false,
    AllowedFieldsPolicy.allowAll());

QueryPlan<Order> locked = new QueryPlan<>(plan.entityType(), plan.rootCondition(),
    plan.serverCondition(), plan.joins(), plan.fetches(), plan.projections(), plan.selections(),
    plan.projectionType(), plan.groupBy(), plan.having(), plan.sort(), plan.distinct(),
    plan.allowedFieldsPolicy(), new QueryLock(LockMode.PESSIMISTIC_WRITE, LockWait.SKIP_LOCKED));
```

After:

```java
QueryPlan<Order> plan = SpecificationQueryBuilder.forEntity(Order.class)
    .where("status", Operators.EQUALS, "PLACED")
    .sort(Sort.by("placedAt"))
    .build();

QueryPlan<Order> locked = plan.toBuilder()
    .lock(LockMode.PESSIMISTIC_WRITE, LockWait.SKIP_LOCKED)
    .build();
```

Migration: replace each `new QueryPlan<>(...)` with the builder calls that produce the same plan.
To change a plan you received, for example from `@FilterableQuery`, call `plan.toBuilder()`: the
conditions added through it are server conditions, and everything else is kept unless changed (see
[Extending a plan received over HTTP](../README.md#extending-a-plan-received-over-http)).
`QueryPlan.withoutFetches()` is public only for the JPA module and is `@API(status = INTERNAL)`.

### Types marked `INTERNAL`

No type changed package or visibility, but the following are now `@API(status = INTERNAL)`: they
may change in any release, including a patch. Code that uses them directly should move to the
`STABLE` or `MAINTAINED` types:

- `com.borjaglez.specrepository.jpa.support`: `AggregateExpressionFactory`, `AssociationRegistry`,
  `DefaultOperatorHandlers`, `DefaultValueConverters`, `OperatorRegistry`, `PathResolver`,
  `QueryPlanSpecificationFactory`, `ValueConversionService`. `SpecificationRepositoryConfiguration`
  in the same package is `MAINTAINED`: customise it through a `SpecificationRepositoryCustomizer`.
- `SpecificationRepositoryImpl` and `SpecificationRepositoryFactoryBean`: use the
  `SpecificationRepository` interface and `@EnableSpecificationRepositories`.
- `QueryPlanArgumentResolver` and `HttpFilterAutoConfiguration`: use `@FilterableQuery`.
- The `SpecificationRepositoryAutoConfiguration` classes of both starters. Excluding them by name
  (`spring.autoconfigure.exclude`) keeps working.

### `apiguardian-api` is a compile-only dependency

The library modules depend on `org.apiguardian:apiguardian-api` as `compileOnlyApi`, as JUnit 5
does. Applications do not need it at runtime and nothing changes in their dependencies. A compiler
that reads the library's classes without the annotation on the classpath may warn about the missing
`org.apiguardian.api.API`; add `org.apiguardian:apiguardian-api` as a `compileOnly` dependency to
silence it.

### HTTP filters limit the number and the length of values

Issue #123.

`HttpFilterParser` now bounds two inputs it used to accept without a limit, and rejects a request
above either of them with `HttpFilterSyntaxException` (HTTP 400 with the usual mapping) before any SQL
runs:

- **`maxValuesPerFilter`** (default 100): the number of values of an `in` or `notin` filter. Before,
  `?filter=id:in:1|2|...` could reach the database as an `IN` list of any size. `between` keeps its
  rule of exactly two non-empty values and its error message.
- **`maxValueLength`** (default 1000): the length, in characters, of a single value, in `filter` and
  in `orFilter` groups. Each value of a multi-value filter is checked on its own.

The message names the field and the limit, without echoing the value:

```text
Invalid filter expression 'id:in': too many values (max 100) for field 'id'
Invalid filter expression 'name:contains': value too long (max 1000 characters) for field 'name'
```

Requests that succeeded before and exceed a limit now get a 400. If your clients legitimately send
more values or longer values, raise the limits on the parser configuration (both must be at least 1):

```java
HttpFilterParserConfiguration config = HttpFilterParserConfiguration.builder()
        .maxValuesPerFilter(500)
        .maxValueLength(4000)
        .build();
```

With the Spring Boot starters, declare an `HttpFilterParser` bean built with that configuration; it
takes precedence over the auto-configured one.

### Client filter errors answer 400 Problem Details by default

Issue #153.

In a Spring MVC application with Spring Boot, the HTTP module now registers a
`@RestControllerAdvice` that answers the client errors of the filter API with **400** and an RFC
9457 Problem Details body (`application/problem+json`). Without a handler of your own, these
requests used to end in a **500**:

| Exception | Before | Now |
|---|---|---|
| `HttpFilterSyntaxException` | 500 | 400 |
| `HttpUnknownOperatorException` | 500 | 400 |
| `DisallowedFieldException` (argument resolver, or `Pageable` sort when the query runs) | 500 | 400 |
| `InvalidFilterException` | 500 | 400 |
| `InvalidFilterValueException` | 500 | 400 |
| `UndeclaredFieldListException` | 400, empty body | 400, Problem Details body |

The exceptions raised when the query runs are handled also when they arrive as the cause of an
`InvalidDataAccessApiUsageException`. The body's `detail` is the exception message, and `field`
names the field when the exception carries one. `IllegalStateException`, any other
`IllegalArgumentException` and an `InvalidDataAccessApiUsageException` with another cause keep
their status.

Applications that already map these exceptions are unaffected: the advice has the lowest
precedence, so a controller `@ExceptionHandler` or an application `@RestControllerAdvice` for the
same types still wins. Check:

- dashboards, alerts or tests that expected a 500 (or a servlet exception in `MockMvc`) for an
  invalid filter;
- application advices with a catch-all `@ExceptionHandler(Exception.class)`: it still receives
  these exceptions, so they keep the status it returns.

To keep the previous behaviour, set:

```yaml
specrepository:
  http:
    problem-details:
      enabled: false
```

## Upgrading to 0.4.0

0.4.0 has seven breaking changes. Most applications only need the first three checks:

1. Endpoints with `@FilterableQuery(Entity.class)` and no field lists now reject every filter and
   sort ([`@FilterableQuery` denies by default](#filterablequery-without-field-lists-denies-by-default)).
2. Queries that `select(...)` or aggregate without `selectInto(...)` must be read with `findRows()` /
   `findRow()` ([rows instead of entities](#selections-are-read-as-rows-not-entities)).
3. Filters on collection paths with `neq`, `notin`, `notcontains`, or the same collection path
   repeated in an AND, return different rows
   ([collection paths](#negated-and-repeated-conditions-on-a-collection-path-use-exists)).

The other changes affect code that catches specific exceptions, sorts through a client `Pageable`
with a restrictive policy, or constructs or deconstructs `QueryPlan` directly.

### `@FilterableQuery` without field lists denies by default

Pull request #109.

Up to 0.3.x a `@FilterableQuery` parameter that declared neither `filterableFields` nor
`sortableFields` allowed filtering and sorting by every field. It now allows neither. A request that
filters or sorts through a usage with no declared list fails while the argument is resolved with
`UndeclaredFieldListException` (package `com.borjaglez.specrepository.http.spring`). It extends
`DisallowedFieldException`, so existing handlers still match, and it is annotated with
`@ResponseStatus(BAD_REQUEST)`, so Spring MVC answers 400 even without a handler. Its message names
the missing attribute:

```text
Field 'name' is not allowed for filtering: @FilterableQuery declares no filterableFields.
Declare filterableFields, or set allowAllFields = true to allow every field.
```

Endpoints that declared only one of the lists behave as before: the other usage was already denied.

Before:

```java
@GetMapping("/products")
Page<Product> list(@FilterableQuery(Product.class) QueryPlan<Product> plan, Pageable pageable) {
    return productRepository.findAll(plan, pageable);
}
```

After (recommended, declare what the client may use):

```java
@GetMapping("/products")
Page<Product> list(
        @FilterableQuery(
                value = Product.class,
                filterableFields = {"name", "status", "category.name"},
                sortableFields = {"name", "price"})
                QueryPlan<Product> plan,
        Pageable pageable) {
    return productRepository.findAll(plan, pageable);
}
```

After (keep the old behaviour explicitly):

```java
@FilterableQuery(value = Product.class, allowAllFields = true) QueryPlan<Product> plan
```

Migration:

- Search for `@FilterableQuery` usages (including composed annotations that use it as a
  meta-annotation) that declare no lists, and add `filterableFields` and `sortableFields`, or
  `allowAllFields = true`.
- `allowAllFields = true` cannot be combined with `filterableFields` or `sortableFields`: that
  combination fails with an `IllegalStateException` when the parameter is resolved.

### Selections are read as rows, not entities

Pull request #105.

A query with `select(...)` or aggregate selections (`sum`, `avg`, `count`, ...) and no
`selectInto(...)` used to return its values typed as the entity, which failed later with a
`ClassCastException`. The entity terminals now reject such a plan with an `IllegalStateException`
("The query selects fields or aggregates, so it returns rows, not entities: read it with findRows()
or findRow(), or map it with selectInto(...)"). This applies to `findAll()`, `findAll(Pageable)`,
`findSlice(Pageable)` and `findOne()` on the fluent query, and to `findAll`, `findSlice` and
`findOne` taking a `QueryPlan` on the repository. Through the Spring Data repository proxy the
exception arrives as `InvalidDataAccessApiUsageException`.

New row terminals read those plans as `GroupedRow` values: `findRows()` (`List<GroupedRow>`) and
`findRow()` (`Optional<GroupedRow>`, reads only the first row) on the fluent query, and
`findRows(QueryPlan)` / `findRow(QueryPlan)` on the repository. `findAllGrouped()` is kept as an
alias of `findRows()`. Plans with `selectInto(...)` behave as before.

| Before (0.3.x) | After (0.4.0) |
|----------------|---------------|
| `List<?> names = repository.query().select("name").findAll();` | `List<GroupedRow> names = repository.query().select("name").findRows();` then `row.get("name")` |
| `Optional<?> total = repository.query().sum("age").findOne();` | `Optional<GroupedRow> total = repository.query().sum("age").findRow();` then `row.get(0)` or `row.get("SUM_age")` |
| `repository.query().select(...).findAll(pageable)` / `findSlice(pageable)` | `repository.query().select(...).selectInto(Dto.class).findAll(pageable)` / `findSlice(pageable)` |
| `repository.findAll(planWithSelections)` | `repository.findRows(plan)` / `repository.findRow(plan)` |

An aggregate column is named `<FUNCTION>_<field>` (for example `SUM_age`) unless it was given an
alias with `sumAs`, `countAs`, and so on.

### Negated and repeated conditions on a collection path use `EXISTS`

Pull request #111.

Every condition on a collection path (an `@ElementCollection` such as `tags`, or a path through a
collection association such as `orders.status`) used to test the same joined element. In entity
queries, `count()` and pages, 0.4.0 changes two cases:

- **Negative operators mean "no element matches".** `neq`, `notin` and `notcontains` on a
  collection path become `NOT EXISTS` of the positive operator (`eq`, `in`, `contains`).
  `tags neq a` now means "does not have tag `a`" and includes roots with an empty collection. Before,
  it meant "has some tag other than `a`". `includeNulls` adds nothing to these conditions.
- **The same collection path repeated under an AND tests one element per condition.** Each
  condition gets its own correlated `EXISTS`: `tags eq a AND tags eq b` now returns the roots that
  have both tags, where it used to match nothing. This also holds when one condition is in a nested
  group (`tags eq a AND (tags eq b OR name eq x)`) and between the client conditions and the server
  conditions of a plan.

Unchanged: different attributes of the same element (`orders.status` and `orders.total`) and the
alternatives of an OR group still share one join, and grouped and projected queries (`groupBy`,
`select`, aggregates, `selectInto`) keep every condition on the shared join. Conditions translated to
`EXISTS` add no outer join, so they no longer make the query `distinct` and no longer restrict a
fetched collection.

Before (0.3.x meaning of `tags neq a`, "has some tag other than `a`"):

```java
customerRepository.query()
    .where("tags", Operators.NOT_EQUALS, "a")
    .findAll();
```

After (same meaning in 0.4.0):

```java
customerRepository.query()
    .<String>exists("tags", sub -> sub.where("value", Operators.NOT_EQUALS, "a"))
    .findAll();
```

Migration:

- Review filters that use `neq`, `notin` or `notcontains` on a collection path, and filters that
  repeat a collection path in an AND, including HTTP filters such as
  `?filter=tags:neq:a`. If the old meaning is needed, write the `exists(...)` subquery explicitly.
- A custom `OperatorHandler` registered for `neq`, `notin` or `notcontains` is no longer called on a
  collection path: the handler of the positive operator runs inside the `NOT EXISTS`.

### Client filters and server conditions are separate in `QueryPlan`

Pull request #106.

The `AllowedFieldsPolicy` now guards client input only. `QueryPlan` has a new component,
`serverCondition`, an AND group placed right after `rootCondition`. It is ANDed with the client
conditions when the query runs and is not checked against the policy, so a server can scope a
client plan by fields the client may not filter by, and a client `orFilter` cannot widen that scope.
A `serverCondition` that is not an AND group is rejected with an `IllegalArgumentException`.

To add server conditions, derive the plan with `plan.toBuilder()`, `SpecificationQueryBuilder.from(plan)`
or `repository.query(plan)`. Conditions added through the derived builder go to `serverCondition`.
`sort(...)` replaces the client sort and the new `sortedByDefault(...)` sets a sort only when the
client sent none. The original plan is not changed.

Before (0.3.x, a server condition ANDed into `rootCondition` by hand, still checked against the
policy, so `customerId` had to be filterable by the client too):

```java
GroupCondition scoped = new GroupCondition(LogicalOperator.AND, List.of(
    plan.rootCondition(),
    new PredicateCondition("customerId", Operators.EQUALS, customer.id(), false, false)));
QueryPlan<Order> serverPlan = new QueryPlan<>(Order.class, scoped, plan.joins(), plan.fetches(),
    plan.projections(), plan.selections(), plan.projectionType(), plan.groupBy(), plan.having(),
    plan.sort(), plan.distinct(), plan.allowedFieldsPolicy());
return orderRepository.findAll(serverPlan, pageable);
```

After:

```java
return orderRepository.query(plan)
    .where("customerId", Operators.EQUALS, customer.id())
    .sortedByDefault(Sort.by(Sort.Direction.DESC, "placedAt"))
    .findAll(pageable);
```

Migration:

- Record patterns and deconstruction over `QueryPlan` must add the `serverCondition` component (and
  the `lock` component, see [below](#queryplan-gains-a-lock-component)).
- The previous 12-argument constructor is kept and creates a plan without server conditions, so
  existing constructor calls still compile. Conditions put into `rootCondition` by hand are still
  client conditions and still checked against the policy: move them to a derived builder.
- The client checks moved to `AllowedFieldsPolicy.validate(plan)` (client conditions, `having` and
  sort). `QueryPlanArgumentResolver` now runs it while resolving a `@FilterableQuery` argument, so a
  disallowed filter, `orFilter` or sort throws `DisallowedFieldException` before the handler method
  runs, unwrapped, instead of when the query runs (where the repository proxy wrapped it in
  `InvalidDataAccessApiUsageException`). An `@ExceptionHandler(DisallowedFieldException.class)`
  keeps working; code that caught the exception inside the handler method no longer sees it.
- The derived plan keeps the client policy, so a sort set with `sort(...)` or `sortedByDefault(...)`
  is still checked against the sortable fields: pick a default sort among them.

### The `Pageable` sort is checked against the plan's policy

Pull request #110.

A sorted `Pageable` overrides the plan sort, but its properties were never checked against the
plan's `AllowedFieldsPolicy`, so a client could order by any path. Now every `Sort.Order` of a sorted
`Pageable` is checked with `AllowedFieldsPolicy.validateSort` before any query is built, in
`findAll(plan, pageable)`, `findSlice`, `findAllProjected`, `findSliceProjected` and the fluent
`findAll(Pageable)` / `findSlice(Pageable)`. A property outside a restrictive policy throws
`DisallowedFieldException` (usage `sorting`) instead of returning data ordered by it; through the
repository proxy it arrives wrapped in `InvalidDataAccessApiUsageException`.

An unsorted `Pageable` keeps the plan sort, validated as before, and `AllowedFieldsPolicy.allowAll()`
plans accept any sort.

Migration:

- If clients sort through the Spring Data `sort` parameter (or a custom
  `spring.data.web.sort.sort-parameter`), add those properties to `sortableFields`, or expect a
  `DisallowedFieldException` for them and map it to 400.
- Passing the controller's `Pageable` unchanged to the repository is safe; there is no need to strip
  its sort.

### Unconvertible filter values throw `InvalidFilterValueException`

Pull request #104.

A filter or `having` value that cannot be converted to the field's Java type (`abc` on a numeric
field, an unknown enum constant, an unparsable date) used to surface as a raw
`ConversionFailedException`, `DateTimeParseException` or `NumberFormatException`. It now throws
`InvalidFilterValueException` (package `com.borjaglez.specrepository.core`), a subclass of
`InvalidFilterException` and so of `IllegalArgumentException`, with:

- `field()`: the condition's (or `having`'s) field;
- `value()`: the value that failed, for `in`, `notin` and `between` the offending element;
- `targetType()`: the conversion target;
- `reason()`: `cannot convert '<value>' to <TargetSimpleName>`;
- the original exception as the cause.

Spring `ConversionException`s, `java.time.DateTimeException`s and `IllegalArgumentException`s thrown
while converting are translated; any other runtime exception from a custom `ValueConverter`
propagates unchanged. Through the repository proxy the new exception arrives wrapped in
`InvalidDataAccessApiUsageException`.

Before:

```java
try {
    repository.query().where("age", Operators.EQUALS, "abc").findAll();
} catch (ConversionFailedException ex) {
    // ...
}
```

After:

```java
try {
    repository.query().where("age", Operators.EQUALS, "abc").findAll();
} catch (InvalidFilterValueException ex) {
    // ex.field() is "age", ex.value() is "abc", ex.targetType() is the type of age
}
```

Migration: replace `catch`/`@ExceptionHandler` clauses for `ConversionFailedException`,
`DateTimeParseException` or `NumberFormatException` around repository calls with
`InvalidFilterValueException` (or `InvalidFilterException` to also cover unknown fields and
operators). Spring MVC `@ExceptionHandler` methods also match an exception's causes, so one
`@ExceptionHandler(InvalidFilterException.class)` covers both the direct and the wrapped case.

### `QueryPlan` gains a `lock` component

Pull request #113.

Pessimistic row locks (see [New features](#new-features)) are part of the plan: `QueryPlan` has a new
last component, `lock` (a `QueryLock`, `QueryLock.NONE` for no lock). The canonical constructor now
takes 14 arguments, the last one a `QueryLock`, and rejects a `null` lock.

Migration:

- The previous canonical constructor (13 arguments, ending with `allowedFieldsPolicy`) and the older
  overloads are kept and create an unlocked plan, so existing calls still compile.
- Code that calls the canonical constructor must pass a `QueryLock` (`QueryLock.NONE` keeps the
  previous behaviour), and record patterns or deconstruction over `QueryPlan` must add the component.

### New features

Non-breaking changes in 0.4.0 worth knowing when upgrading:

- **Pessimistic locking** (#113): `lock(LockMode)` / `lock(LockMode, LockWait)` on `QueryPlanBuilder`
  and the fluent query, with `LockMode.PESSIMISTIC_READ` / `PESSIMISTIC_WRITE` and
  `LockWait.WAIT` / `NOWAIT` / `SKIP_LOCKED`, for example a transactional outbox relay reading a
  batch with `FOR UPDATE SKIP LOCKED`. It needs a read-write transaction, applies to the entity
  terminals only and fails fast with `IllegalStateException` on combinations the databases reject.
  See [Pessimistic Locking](../README.md#pessimistic-locking).
- **Derived plans** (#106): `plan.toBuilder()`, `SpecificationQueryBuilder.from(plan)`,
  `repository.query(plan)` and `sortedByDefault(...)`. See
  [Extending a plan received over HTTP](../README.md#extending-a-plan-received-over-http).
- **Unknown operators and fields throw `InvalidFilterException`** (#102): an operator with no
  registered `OperatorHandler` (at any nesting level) and an unknown path segment fail with
  `InvalidFilterException` (`com.borjaglez.specrepository.core`, an `IllegalArgumentException`,
  message `Invalid filter on field '<field>': <reason>`) before any SQL runs. An unknown operator
  used to throw `IllegalStateException` from the query, so code catching that should catch
  `InvalidFilterException` instead. `OperatorRegistry.get` still throws `IllegalStateException`;
  the new `OperatorRegistry.find` returns an `Optional<OperatorHandler>`.
- **`caseInsensitiveFields`** (#103): a new `@FilterableQuery` attribute lists the fields matched
  case-insensitively for `eq`, `neq`, `contains`, `notcontains`, `startswith` and `endswith`; the
  client filter syntax does not change. `HttpFilterParser` has a matching
  `toQueryPlan(entityType, params, policy, caseInsensitiveFields)` overload.
- **Composed annotations** (#101): `@FilterableQuery` can be used as a meta-annotation, including
  `@AliasFor` overrides, so endpoints exposing the same entity can share one declaration.
