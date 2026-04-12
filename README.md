# spring-boot-specification-repository

[![Maven Central](https://img.shields.io/maven-central/v/com.borjaglez/specification-repository-core)](https://central.sonatype.com/artifact/com.borjaglez/specification-repository-core)
[![CI](https://github.com/borja-glez/spring-boot-specification-repository/actions/workflows/ci.yml/badge.svg)](https://github.com/borja-glez/spring-boot-specification-repository/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/borja-glez/spring-boot-specification-repository)](LICENSE)
![Java 21+](https://img.shields.io/badge/Java-21%2B-blue)

Extensible Spring Data JPA query library with a fluent DSL and native-friendly architecture.

## Features

- Fluent query DSL for chained `where`, `and`, `or`, `join`, and `fetch` operations
- Aggregate projections with `sum`, `avg`, `min`, `max`, and `count(field)`
- Pure builder model -- the builder only creates an immutable query plan
- `SpecificationRepository` as a repository base abstraction for execution
- Per-query field whitelisting for secure API exposure (`AllowedFieldsPolicy`)
- Pluggable operators, predicate factories, converters, and dialect extensions
- GraalVM-aware path resolution based on JPA metamodel metadata instead of reflection-heavy lookup
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
implementation("com.borjaglez:specification-repository-boot3-starter:0.1.0")
```

**Maven**

```xml
<dependency>
    <groupId>com.borjaglez</groupId>
    <artifactId>specification-repository-boot3-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Spring Boot 4

**Gradle**

```kotlin
implementation("com.borjaglez:specification-repository-boot4-starter:0.1.0")
```

**Maven**

```xml
<dependency>
    <groupId>com.borjaglez</groupId>
    <artifactId>specification-repository-boot4-starter</artifactId>
    <version>0.1.0</version>
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

`select(...)` now affects the executed JPA query. Use `selectInto(...)` when you want typed DTO or
record projections.

```java
List<NameOnly> names = productRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .sort(Sort.by("name"))
    .select("name")
    .selectInto(NameOnly.class)
    .findAll();

record NameOnly(String name) {}
```

Projection behavior:

- one selected field returns scalar values at runtime (for example `List<String>`)
- one aggregate function returns a scalar value at runtime (for example `Optional<Double>` from `avg(...)`)
- multiple selected fields return `Object[]` rows at runtime
- grouped aggregate queries can combine `select(...)` and aggregate functions, returning `Object[]` rows at runtime
- constructor-based DTO and record projections are supported through `selectInto(...)`
- `select(...)` and/or aggregate selection methods must be called before `selectInto(...)`
- projected wrappers only expose terminal operations plus plan inspection; no further mutation is available after `selectInto(...)`
- fetch joins are intended for entity loading and should not be combined with projections

### Aggregate Queries

Aggregate functions use the same projection pipeline as `select(...)`.

```java
Optional<?> totalAge = customerRepository.query()
    .where("status", Operators.IS_NOT_NULL, null)
    .sum("age")
    .findOne();

Optional<?> averageAge = customerRepository.query()
    .avg("age")
    .findOne();

List<?> grouped = customerRepository.query()
    .groupBy("status")
    .sort(Sort.by("status"))
    .select("status")
    .count("id")
    .sum("age")
    .findAll();
```

Notes:

- non-grouped aggregate queries return a single row
- grouped aggregate queries return one row per group
- `count(field)` counts non-null values for the selected field
- `sum(...)` and `avg(...)` require numeric fields

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
List<?> groupedTotals = productRepository.query()
    .where("status", Operators.IS_NOT_NULL, null)
    .groupBy("status")
    .sort(Sort.by("status"))
    .select("status")
    .count("id")
    .sum("price")
    .findAll();
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
`allowedFields()`, all fields are permitted (backward-compatible default).

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
List<?> projected = productRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .select("name", "category.name")
    .findAll();

List<?> grouped = productRepository.query()
    .where("status", Operators.IS_NOT_NULL, null)
    .groupBy("status")
    .select("status")
    .count("id")
    .findAll();

List<ProductSummary> typed = productRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .select("name", "category.name")
    .selectInto(ProductSummary.class)
    .findAll();

record ProductSummary(String name, String categoryName) {}
```

- `select(...)` affects the executed JPA query
- aggregate functions use the same projection pipeline as `select(...)`
- one selected field returns scalar values at runtime
- one aggregate function returns a scalar value at runtime
- multiple selected fields return `Object[]` rows at runtime
- grouped `select(...)` + aggregate combinations return `Object[]` rows at runtime
- `selectInto(...)` maps the current selection list into a constructor-based DTO or record
- `groupBy(...)` is applied to the generated `CriteriaQuery`
- grouped `count()` and grouped aggregate queries honor the same filters as `findAll()`
- nested paths and aggregate selections can be combined before `selectInto(...)`
- constructor argument order must match the declared selection order

## Available Operators

| Operator | Description | Example |
|---|---|---|
| `EQUALS` | Equality comparison | `.where("status", Operators.EQUALS, "ACTIVE")` |
| `NOT_EQUALS` | Negated equality | `.where("status", Operators.NOT_EQUALS, "DISCONTINUED")` |
| `CONTAINS` | SQL `LIKE '%value%'` | `.where("name", Operators.CONTAINS, "Pro")` |
| `NOT_CONTAINS` | Negated `LIKE '%value%'` | `.where("name", Operators.NOT_CONTAINS, "test")` |
| `STARTS_WITH` | SQL `LIKE 'value%'` | `.where("name", Operators.STARTS_WITH, "Mac")` |
| `ENDS_WITH` | SQL `LIKE '%value'` | `.where("email", Operators.ENDS_WITH, "@example.com")` |
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
`QueryPlan` so controllers do not need hand-written parsing code. It depends only on
`specification-repository-core` and exposes a Spring MVC argument resolver that is auto-configured
when Spring Web is on the classpath. Works with both Spring Boot 3 and Spring Boot 4.

**Gradle**

```kotlin
implementation("com.borjaglez:specification-repository-http:0.1.0")
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
  `isnotempty`, `notcontains`).
- **Multi-value operators** (`in`, `notin`, `between`): pipe-separated values — `status:in:ACTIVE|PENDING`,
  `price:between:10|100`.
- **Valueless operators** (`isnull`, `isnotnull`, `isempty`, `isnotempty`): value omitted —
  `description:isnull`.
- **OR groups**: `orFilter=field:op:val;field:op:val` — semicolon-separated filters inside a single
  OR group. Repeatable for multiple independent groups.
- **Sorting**: `sort=field,direction` (repeatable; direction is `asc` or `desc`, default `asc`).
- **Pagination**: handled by Spring's standard `Pageable` resolver — this module does not parse
  `page`/`size`.

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
- `HttpUnknownOperatorException` — thrown when the operator is not in the configured
  `allowedOperators` set.

Both extend `IllegalArgumentException` and propagate to the caller so applications can map them
to HTTP 400 responses via their preferred error handling strategy (`@ControllerAdvice`,
`ProblemDetail`, etc.). The module does not register its own exception handler.

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
