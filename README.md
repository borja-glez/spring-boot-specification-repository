# spring-boot-specification-repository

[![Maven Central](https://img.shields.io/maven-central/v/com.borjaglez.specrepository/specification-repository-core)](https://central.sonatype.com/artifact/com.borjaglez.specrepository/specification-repository-core)
[![CI](https://github.com/borja-glez/spring-boot-specification-repository/actions/workflows/ci.yml/badge.svg)](https://github.com/borja-glez/spring-boot-specification-repository/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/borja-glez/spring-boot-specification-repository)](LICENSE)
![Java 21+](https://img.shields.io/badge/Java-21%2B-blue)

Extensible Spring Data JPA query library with a fluent DSL and native-friendly architecture.

## Features

- Fluent query DSL for chained `where`, `and`, `or`, `join`, and `fetch` operations
- Correlated subqueries: `exists` / `notExists` (association or entity-based) and `inSubquery` / `notInSubquery`
- Aggregate projections with `sum`, `avg`, `min`, `max`, and `count(field)`, plus aliasing, `having(...)` and structured `GroupedRow` results (see [docs/reporting.md](docs/reporting.md))
- Pure builder model -- the builder only creates an immutable query plan
- `SpecificationRepository` as a repository base abstraction for execution
- Per-query field whitelisting of client input for secure API exposure (`AllowedFieldsPolicy`), plus
  server conditions added to a plan received over HTTP (`repository.query(plan)`)
- Pluggable operators, predicate factories, converters, and dialect extensions
- GraalVM-aware path resolution based on JPA metamodel metadata instead of reflection-heavy lookup
  (`selectInto(...)` DTOs need a reflection hint in a native image, see [GraalVM Native Image](#graalvm-native-image))
- Spring Boot 3 and Spring Boot 4 starter modules
- 100% JaCoCo coverage enforced (no exclusions)
- Testcontainers-backed integration coverage and runnable demo applications

## Modules

| Module | Description |
|---|---|
| `specification-repository-core` | Query DSL, immutable query model, and extension contracts |
| `specification-repository-jpa` | JPA compiler, repository implementation, and metamodel path resolution |
| `specification-repository-boot3-starter` | Spring Boot 3 auto-configuration |
| `specification-repository-boot4-starter` | Spring Boot 4 auto-configuration |
| `specification-repository-http` | Optional HTTP query parameter parser and Spring MVC argument resolver |
| `specification-repository-test-support` | Shared test fixtures and utilities |
| `examples` | Runnable sample applications |

The `specification-repository-core` and `specification-repository-jpa` modules stay Spring/Boot integration agnostic at the build level. Version alignment is intentionally owned by the Boot 3 and Boot 4 starters and example applications.

## Quick Start

### Spring Boot 3

**Gradle**

```kotlin
implementation("com.borjaglez.specrepository:specification-repository-boot3-starter:0.3.1")
```

**Maven**

```xml
<dependency>
    <groupId>com.borjaglez.specrepository</groupId>
    <artifactId>specification-repository-boot3-starter</artifactId>
    <version>0.3.1</version>
</dependency>
```

### Spring Boot 4

**Gradle**

```kotlin
implementation("com.borjaglez.specrepository:specification-repository-boot4-starter:0.3.1")
```

**Maven**

```xml
<dependency>
    <groupId>com.borjaglez.specrepository</groupId>
    <artifactId>specification-repository-boot4-starter</artifactId>
    <version>0.3.1</version>
</dependency>
```

## Setup

### 1. Enable Specification Repositories

When you use the Boot starter, repository activation is automatic and follows Spring Boot's
Data JPA auto-configuration package scanning. A regular `@SpringBootApplication` is enough:

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

If you prefer manual configuration without the starter, use `@EnableSpecificationRepositories`
or configure `@EnableJpaRepositories` directly:

```java
@EnableJpaRepositories(
    basePackages = "com.example.repositories",
    repositoryBaseClass = SpecificationRepositoryImpl.class)
```

### 2. Define Your Repository

Extend `SpecificationRepository` -- no additional methods needed:

```java
public interface ProductRepository extends SpecificationRepository<Product, Long> {
}
```

### 3. Query with the Fluent DSL

```java
List<Product> products = productRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .where("category.name", Operators.EQUALS, "Electronics")
    .leftFetch("category")
    .sort(Sort.by("price"))
    .findAll();
```

The builder creates an immutable query plan. The repository executes it.

### Projection Queries

`select(...)` affects the executed JPA query. A query with selections returns rows, not entities:
map them with `selectInto(...)` into a DTO or record, or read them as `GroupedRow` with `findRows()`
/ `findRow()`.

```java
List<NameOnly> names = productRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .sort(Sort.by("name"))
    .select("name")
    .selectInto(NameOnly.class)
    .findAll();

record NameOnly(String name) {}

List<GroupedRow> rows = productRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .select("name", "category.name")
    .findRows();
String first = (String) rows.get(0).get("name");
```

Projection behavior:

- `findRows()` returns `List<GroupedRow>`, one row per result, with lookup by column name or index
- `findRow()` returns `Optional<GroupedRow>`, reading only the first row
- the entity terminals (`findAll()`, `findAll(Pageable)`, `findSlice(Pageable)`, `findOne()`, and the
  repository methods that take a `QueryPlan`) reject a query with selections and no `selectInto(...)`
  with an `IllegalStateException`: selected values are never returned typed as the entity
- to paginate selected fields or aggregates, map them with `selectInto(...)`
- constructor-based DTO and record projections are supported through `selectInto(...)`; in a GraalVM
  native image the DTO constructors must be registered for reflection (see
  [GraalVM Native Image](#graalvm-native-image))
- `select(...)` and/or aggregate selection methods must be called before `selectInto(...)`
- projected wrappers only expose terminal operations plus plan inspection; no further mutation is available after `selectInto(...)`
- fetch joins are intended for entity loading and should not be combined with projections

### Aggregate Queries

Aggregate functions use the same projection pipeline as `select(...)`.

```java
Optional<GroupedRow> totalAge = customerRepository.query()
    .where("status", Operators.IS_NOT_NULL, null)
    .sum("age")
    .findRow();
Number total = (Number) totalAge.orElseThrow().get("SUM_age");

Double averageAge = (Double) customerRepository.query()
    .avg("age")
    .findRow()
    .map(row -> row.get(0))
    .orElse(null);

List<GroupedRow> grouped = customerRepository.query()
    .groupBy("status")
    .sort(Sort.by("status"))
    .select("status")
    .count("id")
    .sum("age")
    .findRows();
```

Notes:

- non-grouped aggregate queries return a single row, read with `findRow()`
- grouped aggregate queries return one row per group
- `count(field)` counts non-null values for the selected field
- `sum(...)` and `avg(...)` require numeric fields
- aggregates can be aliased (`sumAs("revenue", "amount")`) and filtered with `having(...)`
- `findRows()` (and its alias `findAllGrouped()`) returns a list of `GroupedRow`, supporting lookup by
  alias or column name (`FUNCTION_field`, for example `SUM_age`, without an alias)

See [docs/reporting.md](docs/reporting.md) for the full reporting and analytical query reference.

## Usage Examples

Assume the following entity model (used across all demo applications):

```java
@Entity
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    @Enumerated(EnumType.STRING)
    private ProductStatus status;
    private LocalDate createdAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
    // getters/setters
}

@Entity
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @OneToMany(mappedBy = "category")
    private List<Product> products = new ArrayList<>();
    // getters/setters
}
```

### Basic Queries

**Equality filter:**

```java
List<Product> active = productRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .findAll();
```

**Text search with CONTAINS:**

```java
List<Product> results = productRepository.query()
    .where("name", Operators.CONTAINS, "iPhone")
    .sort(Sort.by("name"))
    .findAll();
```

**Price range:**

```java
List<Product> inRange = productRepository.query()
    .where("price", Operators.BETWEEN, List.of("50", "200"))
    .sort(Sort.by("price"))
    .findAll();
```

**Null checks:**

```java
List<Product> missing = productRepository.query()
    .where("description", Operators.IS_NULL, null)
    .findAll();
```

### Nested Property Paths

Use dot notation to traverse associations. Intermediate joins are auto-created:

```java
List<Product> electronics = productRepository.query()
    .where("category.name", Operators.EQUALS, "Electronics")
    .leftFetch("category")
    .findAll();
```

That same approach works for deeper paths such as `profile.city` in the integration tests.

### Collections and Shared Joins

A path that ends in a collection of basic values (for example an
`@ElementCollection Set<String> tags`) compares its elements, and a path through
a collection association (`orders.status`) compares the fields of each related
row:

```java
List<Customer> vips = customerRepository.query()
    .where("tags", Operators.EQUALS, "vip")
    .findAll();
```

**Conditions on the same collection path share one join.** Every condition on
`tags` (or on `orders.*`) tests the *same* joined element, so:

- `where("tags", EQUALS, "a").where("tags", EQUALS, "b")` matches nothing: no
  single tag is both `a` and `b`.
- `where("tags", NOT_EQUALS, "a")` means "has some tag other than `a`", not
  "does not have tag `a`".

For per-element semantics, use one `exists` / `notExists` subquery per
condition (see [EXISTS and Subqueries](#exists-and-subqueries)):

```java
// has tag a AND has tag b
customerRepository.query()
    .<String>exists("tags", sub -> sub.where("value", Operators.EQUALS, "a"))
    .<String>exists("tags", sub -> sub.where("value", Operators.EQUALS, "b"))
    .findAll();

// does not have tag a
customerRepository.query()
    .<String>notExists("tags", sub -> sub.where("value", Operators.EQUALS, "a"))
    .findAll();
```

### Logical Groups (AND / OR)

Combine conditions with nested groups:

```java
List<Product> results = productRepository.query()
    .or(group -> group
        .where("name", Operators.CONTAINS, keyword)
        .where("description", Operators.CONTAINS, keyword))
    .sort(Sort.by("name"))
    .findAll();
```

Deeply nested compositions:

```java
List<Customer> customers = customerRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .and(group -> group
        .where("profile.country", Operators.EQUALS, "ES")
        .or(or -> or
            .where("name", Operators.CONTAINS, "Borja")
            .where("email", Operators.CONTAINS, "@example.com")))
    .leftFetch("orders")
    .findAll();
```

### Advanced Filter Composition

For real-world searches, combine root filters, nested groups, explicit joins/fetches, and a reusable plan:

```java
QueryPlan<Product> advancedPlan = productRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .and(group -> group
        .where("category.name", Operators.EQUALS, "Electronics")
        .or(or -> or
            .where("name", Operators.CONTAINS, keyword, true, false)
            .where("description", Operators.CONTAINS, keyword, true, false)))
    .where("price", Operators.GREATER_THAN_OR_EQUAL, "500")
    .where("price", Operators.LESS_THAN_OR_EQUAL, "2500")
    .leftJoin("category")
    .leftFetch("category")
    .sort(Sort.by("price"))
    .plan();

List<Product> products = productRepository.findAll(advancedPlan);
long totalMatches = productRepository.count(advancedPlan);
```

The Boot 3 PostgreSQL demo exposes this as a runnable endpoint:

```text
GET /api/products/advanced/filter-demo?keyword=iphone&category=Electronics&min=500&max=2500
```

### Joins vs Fetches

- **`leftJoin` / `innerJoin` / `rightJoin`** -- create JPA joins for path resolution without eager-loading. Use them when you want an explicit join in the query shape, even though nested path resolution can also auto-create joins.
- **`leftFetch` / `innerFetch` / `rightFetch`** -- create JPA fetch joins. Eager-load associations in a single query to avoid N+1. Fetch instructions are skipped automatically for count queries.

```java
List<Product> products = productRepository.query()
    .where("category.name", Operators.EQUALS, "Books")
    .leftJoin("category")     // explicit join for query shape
    .leftFetch("category")    // eager-load to avoid N+1 on result access
    .findAll();
```

### Pagination

```java
Page<Product> page = productRepository.query()
    .where("status", Operators.NOT_EQUALS, "DISCONTINUED")
    .sort(Sort.by(Sort.Direction.DESC, "createdAt"))
    .findAll(PageRequest.of(0, 10));
```

When using `Pageable`, its sort takes priority over any sort set on the builder.

For large datasets where the total row count is expensive and unnecessary, the
DSL also exposes `findSlice(Pageable)` returning Spring Data's `Slice<T>`. It
fetches `pageSize + 1` rows in a single query, sets `hasNext` accordingly, and
skips the `COUNT(*)` query that `findAll(Pageable)` runs:

```java
Slice<Product> slice = productRepository.query()
    .where("status", Operators.NOT_EQUALS, "DISCONTINUED")
    .sort(Sort.by(Sort.Direction.DESC, "createdAt"))
    .findSlice(PageRequest.of(0, 10));
```

See [docs/pagination.md](docs/pagination.md) for the full `Page` vs `Slice`
comparison and a design note on keyset pagination.

### Grouped Counts

`groupBy(...)` is applied to the underlying JPA Criteria query, so grouped counts honor the same
filters as `findAll()`:

```java
long grouped = productRepository.query()
    .where("status", Operators.IS_NOT_NULL, null)
    .groupBy("status")
    .count();
```

Grouped aggregate queries are also supported through the same execution pipeline:

```java
List<GroupedRow> groupedTotals = productRepository.query()
    .where("status", Operators.IS_NOT_NULL, null)
    .groupBy("status")
    .sort(Sort.by("status"))
    .select("status")
    .count("id")
    .sum("price")
    .findRows();
```

### Single Result and Count

```java
// Single result (first match)
Optional<Product> cheapest = productRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .sort(Sort.by("price"))
    .findOne();

// Count
long total = productRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .count();
```

### EXISTS and Subqueries

For collection associations and cross-entity filters, the DSL supports correlated
subqueries that translate to real SQL `EXISTS` / `IN (SELECT ...)` without
polluting the outer query with joins or row duplication.

**Association-based `EXISTS`** walks an existing outer association:

```java
List<Customer> results = customerRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .<Order>exists("orders", sub -> sub.where("total", Operators.GREATER_THAN, 100))
    .findAll();
```

**`NOT EXISTS`** is the canonical way to express "none of the related rows
match" -- something a plain `where("orders.status", NOT_EQUALS, ...)` cannot do
correctly:

```java
List<Customer> noCancellations = customerRepository.query()
    .<Order>notExists("orders", sub -> sub.where("status", Operators.EQUALS, "CANCELLED"))
    .findAll();
```

**Collections of basic values** work the same way. The subquery root is the
element itself, so the body refers to it with the field `value`:

```java
List<Customer> vips = customerRepository.query()
    .<String>exists("tags", sub -> sub.where("value", Operators.EQUALS, "vip"))
    .findAll();
```

**Entity-based correlation** lets you query any entity class, not just a
navigable association, and correlate explicitly via one or more field pairs:

```java
List<Customer> vipBuyers = customerRepository.query()
    .exists(Order.class, sub -> sub
        .correlate("id", "customer.id")
        .where("status", Operators.EQUALS, "PAID")
        .where("vip", Operators.EQUALS, true))
    .findAll();
```

**`IN (subquery)` and `NOT IN (subquery)`** take the outer field to match, the
sub-entity class, the projected field from the subquery, and the body:

```java
List<Customer> inSubquery = customerRepository.query()
    .inSubquery("id", Order.class, "customer.id",
        sub -> sub.where("vip", Operators.EQUALS, true))
    .findAll();
```

Subquery calls compose with `and` / `or` groups like any other predicate and
respect `AllowedFieldsPolicy` on their outer fields. See
[docs/subqueries.md](docs/subqueries.md) for the full API, correlation
semantics, and current limitations.

### IN Operator with Distinct

```java
List<Product> products = productRepository.query()
    .where("category.name", Operators.IN, List.of("Electronics", "Books"))
    .leftFetch("category")
    .distinct()
    .findAll();
```

### Case-Insensitive Search (PostgreSQL)

The extended `where()` overload exposes `ignoreCase` and `includeNulls` flags:

```java
List<Product> results = productRepository.query()
    .where("name", Operators.CONTAINS, keyword, true, false)
    //                                   ^ignoreCase  ^includeNulls
    .sort(Sort.by("name"))
    .findAll();
```

With the default operator handlers, `ignoreCase = true` normalizes the database expression with
`unaccent(UPPER(path))`. That behavior is demonstrated in the PostgreSQL demos and requires the
PostgreSQL `unaccent` extension to be enabled.

Important: this is NOT a portable SQL abstraction yet. If you run the same overload on another
dialect, you must provide a compatible database function or replace the operator handling strategy.

HTTP endpoints cannot request this flag from the client; the server declares case-insensitive
fields with `@FilterableQuery(caseInsensitiveFields = ...)` (see
[Case-insensitive fields](#case-insensitive-fields)).

### Field Whitelisting

When the DSL is exposed through a public API, restrict which fields clients can filter and sort by:

```java
AllowedFieldsPolicy policy = AllowedFieldsPolicy.of(
    Set.of("name", "email", "status"),   // allowed for filtering
    Set.of("name", "createdAt"));        // allowed for sorting

List<User> users = userRepository.query()
    .allowedFields(policy)
    .where("name", Operators.CONTAINS, searchTerm)
    .sort(Sort.by("createdAt"))
    .findAll();
```

Attempting to filter or sort by a non-whitelisted field throws `DisallowedFieldException`:

```java
// Throws: "Field 'passwordHash' is not allowed for filtering"
userRepository.query()
    .allowedFields(policy)
    .where("passwordHash", Operators.EQUALS, value)
    .findAll();
```

The policy is per-query, so each endpoint can define its own restrictions. Without
`allowedFields()`, all fields are permitted (backward-compatible default) for plans built in
code. HTTP endpoints resolved with `@FilterableQuery` are the opposite: they deny every field that
is not declared (see [Deny by default](#deny-by-default)).

The policy guards **client input**, not the whole query. A plan keeps its conditions in two parts,
combined with AND when the query runs:

- **client conditions** (`QueryPlan.rootCondition()`): the filters a caller chose, such as the ones
  parsed from an HTTP request. They are checked against the policy, together with the plan's sort
  and `having` fields.
- **server conditions** (`QueryPlan.serverCondition()`): conditions the application adds, such as
  "only the current customer's orders", a tenant or a soft-delete flag. They are not checked against
  the policy, so they can use fields the client may not filter by. They are always ANDed with the
  client conditions: a client `orFilter` cannot widen them.

Server conditions are added by deriving a plan (see
[Extending a plan received over HTTP](#extending-a-plan-received-over-http)). What the server
defines itself (selections, `groupBy`, aggregates, joins, fetches and subquery bodies) is not
checked either.

### Pre-Built Query Plans

Build a plan once and reuse it:

```java
QueryPlan<Product> activePlan = productRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .plan();

List<Product> all = productRepository.findAll(activePlan);
Page<Product> page = productRepository.findAll(activePlan, pageable);
long count = productRepository.count(activePlan);
```

This is the safest way to keep list and count endpoints aligned when they must share exactly the
same filters.

### Extending a plan received over HTTP

`plan.toBuilder()` (or `SpecificationQueryBuilder.from(plan)`) returns a builder seeded with every
component of a plan, and `repository.query(plan)` does the same with the repository terminals. The
original plan is not changed. On a derived builder:

- `where`, `and`, `or`, `exists`, ... add **server conditions**: they are ANDed with the client
  conditions and are not checked against the plan's `AllowedFieldsPolicy`;
- the client conditions, the policy, joins, fetches, sort, projection and `distinct` are kept, and
  the other DSL methods (`leftFetch`, `groupBy`, `select`, `selectInto`, ...) add to them;
- `sort(...)` replaces the client sort, and `sortedByDefault(...)` sets a sort only when the client
  sent none.

"My orders": the client filters by status and total, the server scopes the rows to the
authenticated customer, although `customerId` is not in the whitelist:

```java
@GetMapping("/my-orders")
Page<Order> myOrders(
        @AuthenticationPrincipal Customer customer,
        @FilterableQuery(
                value = Order.class,
                filterableFields = {"status", "total"},
                sortableFields = {"placedAt", "total"})
                QueryPlan<Order> plan,
        Pageable pageable) {
    return orderRepository.query(plan)
            .where("customerId", Operators.EQUALS, customer.id())
            .leftFetch("lines")
            .sortedByDefault(Sort.by(Sort.Direction.DESC, "placedAt"))
            .findAll(pageable);
}
```

`?filter=customerId:eq:someone-else` is still rejected with `DisallowedFieldException`, and
`?orFilter=status:eq:PAID;status:eq:PLACED` only matches the customer's own orders:
`(client conditions) AND customerId = ?`.

The derived plan keeps the client's policy, so the sort is still checked against the sortable
fields. That includes a sort set with `sort(...)` or `sortedByDefault(...)`: pick a default sort
among the sortable fields.

### Built-In `BETWEEN`

`BETWEEN` is now available as a standard operator for inclusive numeric and date ranges:

```java
List<Product> createdThisYear = productRepository.query()
    .where("createdAt", Operators.BETWEEN, List.of("2024-01-01", "2024-12-31"))
    .findAll();
```

Important notes:

- `BETWEEN` expects an `Iterable` with exactly 2 values
- invalid inputs fail fast with a clear `IllegalArgumentException`
- bounds are used as provided; they are not reordered automatically

### Current Projection and Aggregate Behavior

The default JPA repository executes `select(...)`, `groupBy(...)`, and aggregate functions with the
following runtime semantics:

```java
List<GroupedRow> projected = productRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .select("name", "category.name")
    .findRows();

List<GroupedRow> grouped = productRepository.query()
    .where("status", Operators.IS_NOT_NULL, null)
    .groupBy("status")
    .select("status")
    .count("id")
    .findRows();

List<ProductSummary> typed = productRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .select("name", "category.name")
    .selectInto(ProductSummary.class)
    .findAll();

record ProductSummary(String name, String categoryName) {}
```

- `select(...)` affects the executed JPA query
- aggregate functions use the same projection pipeline as `select(...)`
- a query with selections and no `selectInto(...)` is read with `findRows()` / `findRow()`, which
  return `GroupedRow` values in the declared selection order; `findAll()`, `findAll(Pageable)`,
  `findSlice(Pageable)` and `findOne()` reject it with an `IllegalStateException`
- a single aggregate without `groupBy(...)` returns one row, read with `findRow()`
- `selectInto(...)` maps the current selection list into a constructor-based DTO or record; the JPA
  provider calls that constructor by reflection, so native images need a hint for the type (see
  [GraalVM Native Image](#graalvm-native-image))
- `groupBy(...)` is applied to the generated `CriteriaQuery`
- `groupBy(...)` does not add its fields to the result rows; select them with `select(...)`, in the order you want the columns
- grouped `count()` and grouped aggregate queries honor the same filters as `findAll()`
- nested paths and aggregate selections can be combined before `selectInto(...)`
- constructor argument order must match the declared selection order

## Available Operators

| Operator | Description | Example |
|---|---|---|
| `EQUALS` | Equality comparison | `.where("status", Operators.EQUALS, "ACTIVE")` |
| `NOT_EQUALS` | Negated equality | `.where("status", Operators.NOT_EQUALS, "DISCONTINUED")` |
| `CONTAINS` | SQL `LIKE '%value%'`; the value is matched literally (`%`, `_` and `\` are escaped) | `.where("name", Operators.CONTAINS, "Pro")` |
| `NOT_CONTAINS` | Negated `LIKE '%value%'`; the value is matched literally | `.where("name", Operators.NOT_CONTAINS, "test")` |
| `STARTS_WITH` | SQL `LIKE 'value%'`; the value is matched literally | `.where("name", Operators.STARTS_WITH, "Mac")` |
| `ENDS_WITH` | SQL `LIKE '%value'`; the value is matched literally | `.where("email", Operators.ENDS_WITH, "@example.com")` |
| `GREATER_THAN` | `>` comparison | `.where("price", Operators.GREATER_THAN, "100")` |
| `GREATER_THAN_OR_EQUAL` | `>=` comparison | `.where("price", Operators.GREATER_THAN_OR_EQUAL, "50")` |
| `LESS_THAN` | `<` comparison | `.where("price", Operators.LESS_THAN, "500")` |
| `LESS_THAN_OR_EQUAL` | `<=` comparison | `.where("price", Operators.LESS_THAN_OR_EQUAL, "200")` |
| `BETWEEN` | Inclusive range comparison | `.where("price", Operators.BETWEEN, List.of("50", "200"))` |
| `IS_NULL` | `IS NULL` check | `.where("description", Operators.IS_NULL, null)` |
| `IS_NOT_NULL` | `IS NOT NULL` check | `.where("description", Operators.IS_NOT_NULL, null)` |
| `IS_EMPTY` | `IS EMPTY` on collections | `.where("orders", Operators.IS_EMPTY, null)` |
| `IS_NOT_EMPTY` | `IS NOT EMPTY` on collections | `.where("orders", Operators.IS_NOT_EMPTY, null)` |
| `IN` | SQL `IN (...)` | `.where("status", Operators.IN, List.of("A", "B"))` |
| `NOT_IN` | Negated `IN (...)` | `.where("status", Operators.NOT_IN, List.of("X"))` |

Custom operators: `Operators.custom("my_operator")` -- register a matching `OperatorHandler` to support them.

## Extension Points

The library is designed to be extended at multiple levels:

### Custom Operators

```java
FilterOperator JSONB_EQUALS = Operators.custom("jsonb_eq");

OperatorHandler handler = new OperatorHandler() {
    @Override public FilterOperator operator() { return JSONB_EQUALS; }
    @Override public Predicate create(OperatorContext ctx) {
        return ctx.criteriaBuilder().function("jsonb_path_exists", ...);
    }
};
```

### Custom Value Converters

Implement `ValueConverter` to handle type conversions for your domain types:

```java
ValueConverter uuidConverter = new ValueConverter() {
    @Override public boolean supports(Class<?> type, FilterOperator op) {
        return UUID.class.isAssignableFrom(type);
    }
    @Override public Object convert(Object value, Class<?> type, FilterOperator op) {
        return value instanceof String s ? UUID.fromString(s) : value;
    }
};
```

### Full Pipeline Customization

Extend `SpecificationRepositoryImpl` and override the `QueryPlanSpecificationFactory` with your own `OperatorRegistry`, `ValueConversionService`, and `PathResolver`.

### Spring Boot Customization

When you use the Boot 3 or Boot 4 starter, the extension points are exposed as beans:

- register `OperatorHandler` beans to add or replace operators
- register `ValueConverter` beans to customize value conversion
- register `SpecificationRepositoryCustomizer` beans to adjust the repository pipeline
- optionally provide `PathResolver`, `ConversionService`, `QueryPlanSpecificationFactory`, or a full `SpecificationRepositoryConfiguration` bean

```java
@Configuration(proxyBeanMethods = false)
class SpecificationRepositoryCustomization {

    @Bean
    OperatorHandler jsonbEqualsOperator() {
        return new OperatorHandler() {
            @Override public FilterOperator operator() {
                return Operators.custom("jsonb_eq");
            }

            @Override public Predicate create(OperatorContext context) {
                return context.criteriaBuilder().isNotNull(context.path());
            }
        };
    }

}
```

Important notes:

- custom `OperatorHandler` beans are registered after the defaults, so they can replace a built-in operator cleanly
- custom `ValueConverter` beans are registered before the defaults, so domain-specific conversion can win first
- providing a `SpecificationRepositoryConfiguration` bean replaces the starter-assembled configuration entirely

## HTTP Filter Parser

The optional `specification-repository-http` module translates HTTP query parameters into a
`QueryPlan` so controllers do not need hand-written parsing code. The parser logic is plain Java,
but the module's public API uses Spring Data Commons types such as `Sort`, so the minimal
required dependencies include `specification-repository-core` and Spring Data Commons. It also
exposes a Spring MVC argument resolver that is auto-configured when Spring Web is on the
classpath. Works with both Spring Boot 3 and Spring Boot 4.

**Gradle**

```kotlin
implementation("com.borjaglez.specrepository:specification-repository-http:0.3.1")
```

### Query Parameter Contract

```
GET /api/products?filter=name:contains:Laptop&filter=status:eq:ACTIVE
                 &orFilter=price:lt:100;price:gt:1000
                 &sort=price,desc&sort=name,asc
                 &page=0&size=20
```

- **Filters**: `filter=field:operator:value` (repeatable, AND-combined at the root level). Operator
  names reuse the existing `Operators` string values (`eq`, `neq`, `contains`, `startswith`,
  `endswith`, `gt`, `gte`, `lt`, `lte`, `between`, `in`, `notin`, `isnull`, `isnotnull`, `isempty`,
  `isnotempty`, `notcontains`). Operator names are case-insensitive: `status:EQ:ACTIVE`,
  `status:Eq:ACTIVE` and `status:eq:ACTIVE` are equivalent, because the parser lower-cases them
  (with `Locale.ROOT`). Custom operators used over HTTP must therefore be registered in lower case
  (`Operators.custom("jsonb_eq")`).
- **Multi-value operators** (`in`, `notin`, `between`): pipe-separated values — `status:in:ACTIVE|PENDING`,
  `price:between:10|100`.
- **Valueless operators** (`isnull`, `isnotnull`, `isempty`, `isnotempty`): value omitted —
  `description:isnull`.
- **OR groups**: `orFilter=field:op:val;field:op:val` — semicolon-separated filters inside a single
  OR group. Repeatable for multiple independent groups.
- **Sorting**: `sort=field,direction` (repeatable; direction is `asc` or `desc`, default `asc`).
- **Pagination**: handled by Spring's standard `Pageable` resolver — this module does not parse
  `page`/`size`.
- **Case sensitivity of values**: matching is case-sensitive unless the server declares the field in
  `@FilterableQuery(caseInsensitiveFields = ...)` (see [Case-insensitive fields](#case-insensitive-fields)).
  Clients cannot choose it; there is no syntax for it.

Field names are validated against a strict pattern (`[a-zA-Z][a-zA-Z0-9_]*` with optional dotted
segments), so paths like `../../secret` are rejected as syntax errors.

### Spring MVC Controller Usage

Annotate a `QueryPlan<T>` controller parameter with `@FilterableQuery` and let the argument
resolver build and whitelist the plan from the request:

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @GetMapping("/filter")
    public Page<Product> filter(
            @FilterableQuery(
                    value = Product.class,
                    filterableFields = {"name", "status", "price", "category.name", "createdAt"},
                    sortableFields = {"name", "price", "createdAt"})
                    QueryPlan<Product> query,
            Pageable pageable) {
        return productRepository.findAll(query, pageable);
    }
}
```

`filterableFields` and `sortableFields` are translated into an `AllowedFieldsPolicy`, so any
client attempting to filter or sort by a non-whitelisted field receives a `DisallowedFieldException`.
The resolver checks the request's filters and sort while it resolves the argument, before the
handler runs, so the exception reaches your `@ExceptionHandler` unwrapped. The plan keeps the policy
and is checked again when it runs.

#### Deny by default

`@FilterableQuery` only accepts the fields it declares:

| Declaration | Filtering | Sorting |
|---|---|---|
| no lists: `@FilterableQuery(Product.class)` | denied | denied |
| only `filterableFields` | declared fields | denied |
| only `sortableFields` | denied | declared fields |
| both lists | declared fields | declared fields |
| `allowAllFields = true` | any field | any field |

A request that filters or sorts through a usage without a declared list is rejected with
`UndeclaredFieldListException`, a `DisallowedFieldException` whose message names the missing
attribute:

```text
Field 'name' is not allowed for filtering: @FilterableQuery declares no filterableFields. Declare filterableFields, or set allowAllFields = true to allow every field.
```

The exception is annotated with `@ResponseStatus(BAD_REQUEST)`, so Spring MVC answers 400 even
without an exception handler; a handler for `DisallowedFieldException` catches it as well. A request
without `filter`, `orFilter` or `sort` parameters is still accepted. The annotation is not checked
at startup.

To accept every field, opt in explicitly. `allowAllFields = true` cannot be combined with
`filterableFields` or `sortableFields` (resolving such a parameter throws `IllegalStateException`),
and it is honoured through composed annotations and `@AliasFor` like the other attributes:

```java
@GetMapping("/internal/products")
public Page<Product> internalSearch(
        @FilterableQuery(value = Product.class, allowAllFields = true) QueryPlan<Product> query,
        Pageable pageable) {
    return productRepository.findAll(query, pageable);
}
```

> **Migrating from 0.3.x:** `@FilterableQuery` without field lists used to allow every field; it now
> allows none. Declare `filterableFields` and `sortableFields` for each such endpoint (recommended),
> or add `allowAllFields = true` to keep the old behaviour. An endpoint that declared only one list
> behaves as before: the other usage was already denied.

To add conditions the client must not control (the current user, a tenant, "on sale only"), derive
the plan with `repository.query(plan)` or `plan.toBuilder()`: see
[Extending a plan received over HTTP](#extending-a-plan-received-over-http).

`@FilterableQuery` also works as a meta-annotation, so endpoints that expose the same entity can
share one declaration instead of repeating the field lists:

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@FilterableQuery(
        value = Product.class,
        filterableFields = {"name", "status", "price"},
        sortableFields = {"name", "price"})
public @interface ProductSearch {}

@GetMapping("/filter")
public Page<Product> filter(@ProductSearch QueryPlan<Product> query, Pageable pageable) { ... }

@GetMapping("/facets")
public Facets facets(@ProductSearch QueryPlan<Product> query) { ... }
```

Composition may be nested (add `ElementType.ANNOTATION_TYPE` to the composed annotation's
`@Target` to meta-annotate it again), and a composed annotation can expose attributes of its own
with `@AliasFor(annotation = FilterableQuery.class)`, for example a `value()` that sets the entity.
A `@FilterableQuery` declared directly on the parameter wins over a meta-present one.

#### Case-insensitive fields

A public search box usually wants `name:contains:cafe` to find "Café de Colombia". The server
declares which fields are matched case-insensitively; the client syntax does not change:

```java
@GetMapping("/search")
public Page<Product> search(
        @FilterableQuery(
                value = Product.class,
                filterableFields = {"name", "description", "status"},
                caseInsensitiveFields = {"name", "description"})
                QueryPlan<Product> query,
        Pageable pageable) {
    return productRepository.findAll(query, pageable);
}
```

- Conditions on those fields, in `filter` and in `orFilter`, are built with `ignoreCase = true`
  when the operator is `eq`, `neq`, `contains`, `notcontains`, `startswith` or `endswith`.
- Other operators on those fields (`gt`, `gte`, `lt`, `lte`, `between`, `in`, `notin`, the null and
  empty checks, and custom operators) are not affected: they keep the default matching and are not
  rejected.
- The attribute does not make a field filterable: list it in `filterableFields` as well (or set
  `allowAllFields = true`).
- The attribute is honoured through composed annotations and `@AliasFor` overrides like the other
  attributes.
- With the default operator handlers, `ignoreCase` compares `unaccent(UPPER(path))` with
  `unaccent(UPPER(value))`, so on PostgreSQL the match is **also accent-insensitive** and requires
  the `unaccent` extension (`CREATE EXTENSION unaccent;`). Other databases need a compatible
  `unaccent` function or custom operator handlers (see
  [Case-Insensitive Search](#case-insensitive-search-postgresql)).

Outside Spring MVC, pass the same set to
`HttpFilterParser.toQueryPlan(entityType, params, policy, caseInsensitiveFields)`.

`HttpFilterAutoConfiguration` is also registered for the `@WebMvcTest` slice on Spring Boot 3 and
Spring Boot 4, so controller slice tests resolve `@FilterableQuery QueryPlan<T>` parameters without
an extra `@ImportAutoConfiguration(HttpFilterAutoConfiguration.class)`. Custom `HttpFilterParser` or
`QueryPlanArgumentResolver` beans still take precedence.

### Programmatic Usage (no Spring)

The parser is a pure Java class and can be used outside of Spring MVC:

```java
HttpFilterParser parser = new HttpFilterParser();

Map<String, List<String>> params = Map.of(
        "filter", List.of("name:contains:Laptop", "status:eq:ACTIVE"),
        "sort",   List.of("price,desc"));

QueryPlan<Product> plan = parser.toQueryPlan(Product.class, params);
List<Product> products = productRepository.findAll(plan);
```

`HttpFilterParserConfiguration` lets you customize parameter names, separators, limits, and an
optional allowed-operators set:

```java
HttpFilterParserConfiguration config = HttpFilterParserConfiguration.builder()
        .filterParam("q")
        .orFilterParam("any")
        .sortParam("order")
        .multiValueSeparator(",")
        .orGroupSeparator("|")
        .maxFilters(10)
        .maxSortFields(3)
        .allowedOperators(Set.of("eq", "contains", "in"))
        .build();

HttpFilterParser parser = new HttpFilterParser(config);
```

### Error Handling

- `HttpFilterSyntaxException` — thrown for malformed filter/sort expressions, invalid field
  names, and filter/sort count limits exceeded.
- `UndeclaredFieldListException` — thrown by the argument resolver when a request filters or sorts
  through a `@FilterableQuery` parameter that declares no list for that usage (see
  [Deny by default](#deny-by-default)). It extends `DisallowedFieldException` and is annotated with
  `@ResponseStatus(BAD_REQUEST)`.
- `HttpUnknownOperatorException` — thrown when the operator is not in the configured
  `allowedOperators` set.

All of them extend `IllegalArgumentException` and propagate to the caller so applications can map them
to HTTP 400 responses via their preferred error handling strategy (`@ControllerAdvice`,
`ProblemDetail`, etc.). The module does not register its own exception handler; only
`UndeclaredFieldListException` carries a `@ResponseStatus`.

Filters that pass the parser can still be invalid for the entity. When the query runs, the JPA
module throws `InvalidFilterException` (in `specification-repository-core`, also an
`IllegalArgumentException`) before any SQL is executed:

- unknown operator: an operator with no registered `OperatorHandler`, at any nesting level
  (`or`/`and` groups and subquery bodies). `field()` is the condition's field and `reason()` is
  `unknown operator '<op>'`;
- unknown field: a path segment the entity does not have, in a filter, join, fetch, subquery,
  `groupBy` or selection. `field()` is the full dotted path, `reason()` is `unknown field` (or `unknown field '<segment>'` when the
  unknown segment is not the last one), and the JPA provider's exception is the cause;
- unconvertible value: a value that cannot be converted to the field's Java type, such as `abc` on
  a numeric field, an unknown enum constant or an unparsable date, in a filter (at any nesting
  level) or a `having` clause. The exception is the subclass `InvalidFilterValueException`:
  `field()` is the condition's field, `value()` the value that failed (for `in`, `notin` and
  `between`, the offending element), `targetType()` the type it was converted to, `reason()` is
  `cannot convert '<value>' to <TargetSimpleName>`, and the original exception
  (`ConversionFailedException`, `DateTimeParseException`, `NumberFormatException`, ...) is the
  cause. Spring `ConversionException`s, `DateTimeException`s and `IllegalArgumentException`s
  thrown while converting are translated; any other exception from a custom `ValueConverter`
  propagates unchanged.

The message is `Invalid filter on field '<field>': <reason>`.

Calls through the DSL (`repository.query()...findAll()`, `count()`, ...) throw the exception
itself. Calls through the Spring Data repository proxy, such as `repository.findAll(plan)`, go
through persistence exception translation and throw `InvalidDataAccessApiUsageException` with the
`InvalidFilterException` as its cause. Spring MVC `@ExceptionHandler` methods also match an
exception's causes, so one handler covers both cases, as long as no handler in the same advice
matches the wrapper itself (for example an `@ExceptionHandler(Exception.class)`):

```java
@RestControllerAdvice
class FilterErrors {
  @ExceptionHandler(InvalidFilterException.class)
  ProblemDetail invalidFilter(InvalidFilterException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }
}
```

`OperatorRegistry.get` still throws `IllegalStateException` for an unknown operator; use
`OperatorRegistry.find` to look a handler up without an exception.

## Demo Applications

The repository includes four demo applications:

| Application | Database | Port | Command |
|---|---|---|---|
| `boot3-demo` | H2 (in-memory) | 8080 | `./gradlew :examples:boot3-demo:bootRun` |
| `boot3-postgres-demo` | PostgreSQL (Docker) | 8082 | `./gradlew :examples:boot3-postgres-demo:bootRun` |
| `boot4-demo` | H2 (in-memory) | 8081 | Check the module task list locally before assuming `bootRun` is available |
| `boot4-postgres-demo` | PostgreSQL (Docker) | 8083 | Check the module task list locally before assuming `bootRun` is available |

The demo REST APIs show the same DSL patterns documented above, including nested filters,
logical groups, reusable plans, aggregate queries, and PostgreSQL-specific text search.

Example aggregate endpoint available in every demo application:

```text
GET /api/products/aggregates/active-summary
```

The Boot 3 H2 demo also exposes the HTTP filter parser via `@FilterableQuery`:

```text
GET /api/products/filter?filter=name:contains:Laptop&filter=status:eq:ACTIVE&sort=price,desc&page=0&size=20
```

That endpoint demonstrates:

- `sum("price")`
- `avg("price")`
- `min("price")`
- `max("price")`
- `count("description")`

Postman collections are available in `examples/`:

- `Specification-Repository-Demo.postman_collection.json` (H2 demos)
- `Specification-Repository-Postgres-Demo.postman_collection.json` (PostgreSQL demos)

## GraalVM Native Image

Specification repositories run in a GraalVM native image without extra hints. Repositories, the
query DSL, subqueries, `select(...)` without `selectInto(...)`, aggregate functions and
`findRows()` / `findRow()` resolve paths through the JPA metamodel and need no reflection registration.
The library itself registers no runtime hints (no `RuntimeHintsRegistrar`, no `aot.factories`).

`selectInto(Dto.class)` is the exception. The repository builds the projection with
`CriteriaBuilder.construct(Dto.class, ...)`, and the JPA provider calls the DTO constructor by
reflection. In a native image every `selectInto(...)` projection type must have its constructors
registered for reflection, unless something else already registers it. A type that is also a Spring
MVC controller return type, for example, already gets binding hints from Spring. A projection used
only internally does not, and it works on the JVM but fails at runtime in a native image when the
provider instantiates it.

```java
record RevenueByDay(LocalDate day, BigDecimal revenue) {}   // used only internally

orders.query().groupBy("day").select("day").sumAs("revenue", "total")
    .selectInto(RevenueByDay.class).findAll();
```

Register the type with `@RegisterReflectionForBinding` on a configuration class:

```java
@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(RevenueByDay.class)
class ProjectionHintsConfiguration {}
```

Or register its constructors with a `RuntimeHintsRegistrar` imported with `@ImportRuntimeHints`:

```java
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(ProjectionHints.class)
class ProjectionHintsConfiguration {}

class ProjectionHints implements RuntimeHintsRegistrar {

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    hints.reflection()
        .registerType(RevenueByDay.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
  }
}
```

The same applies to Spring Boot 3 and Spring Boot 4. Binding hints (the ones Spring registers for
controller return types and the ones `@RegisterReflectionForBinding` registers) are the variant
validated in a native image; the `RuntimeHintsRegistrar` variant is the lower-level equivalent and
has not been exercised in a native image build.

## Configuration

Dependency management is aligned with the Spring Boot BOM -- no explicit version overrides are needed for Spring-managed dependencies.

The starters auto-configure JPA repository scanning from the application's auto-configuration
packages and register `SpecificationRepositoryImpl` as the repository base class. Manual
configuration via `@EnableSpecificationRepositories` or `@EnableJpaRepositories` is still
available when you do not want to use the starters. The starters also contribute a
`SpecificationRepositoryConfiguration` bean so operator handlers, value converters, and
repository customizers can be wired through standard Spring beans. `@EnableSpecificationRepositories`
uses the same repository factory bean, so the same configuration bean can also be supplied in
manual Spring setups.

## Building

Run all tests and coverage verification:

```bash
./gradlew quality
```

Build a single module:

```bash
./gradlew :specification-repository-core:build
```

Run a single test class:

```bash
./gradlew :specification-repository-jpa:test --tests "com.borjaglez.specrepository.jpa.it.SpecificationRepositoryIntegrationTest"
```

Apply code formatting before committing:

```bash
./gradlew :specification-repository-core:spotlessApply
./gradlew :specification-repository-jpa:spotlessApply
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

See [LICENSE](LICENSE).
