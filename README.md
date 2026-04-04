# spring-boot-specification-repository

[![Maven Central](https://img.shields.io/maven-central/v/com.borjaglez/specification-repository-core)](https://central.sonatype.com/artifact/com.borjaglez/specification-repository-core)
[![CI](https://github.com/borja-glez/spring-boot-specification-repository/actions/workflows/ci.yml/badge.svg)](https://github.com/borja-glez/spring-boot-specification-repository/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/borjaglez/spring-boot-specification-repository)](LICENSE)
![Java 21+](https://img.shields.io/badge/Java-21%2B-blue)

Extensible Spring Data JPA query library with a fluent DSL and native-friendly architecture.

## Features

- Fluent query DSL for chained `where`, `and`, `or`, `join`, and `fetch` operations
- Pure builder model -- the builder only creates an immutable query plan
- `SpecificationRepository` as a repository base abstraction for execution
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
| `specification-repository-test-support` | Shared test fixtures and utilities |
| `examples` | Runnable sample applications |

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

Add `@EnableSpecificationRepositories` to your application class:

```java
@SpringBootApplication
@EnableSpecificationRepositories
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

Or configure manually via `@EnableJpaRepositories`:

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

### Joins vs Fetches

- **`leftJoin` / `innerJoin` / `rightJoin`** -- create JPA joins for path resolution without eager-loading. Use when filtering on a related entity but you don't need the data.
- **`leftFetch` / `innerFetch` / `rightFetch`** -- create JPA fetch joins. Eager-load associations in a single query to avoid N+1. Automatically skipped for count queries.

```java
List<Product> products = productRepository.query()
    .where("category.name", Operators.EQUALS, "Books")
    .leftJoin("category")     // needed for the filter
    .leftFetch("category")    // eager-load to avoid N+1
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

When using PostgreSQL, the extended `where()` overload supports case-insensitive and unaccent matching:

```java
List<Product> results = productRepository.query()
    .where("name", Operators.CONTAINS, keyword, true, false)
    //                                   ^ignoreCase  ^includeNulls
    .sort(Sort.by("name"))
    .findAll();
```

This uses `unaccent(UPPER(path))` under the hood. Requires the PostgreSQL `unaccent` extension.

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
| `GREATER_THAN_OR_EQUAL` | `>=` comparison | `.where("price", Operators.GTE, "50")` |
| `LESS_THAN` | `<` comparison | `.where("price", Operators.LESS_THAN, "500")` |
| `LESS_THAN_OR_EQUAL` | `<=` comparison | `.where("price", Operators.LTE, "200")` |
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

## Demo Applications

The repository includes four runnable demo applications:

| Application | Database | Port | Command |
|---|---|---|---|
| `boot3-demo` | H2 (in-memory) | 8080 | `./gradlew :examples:boot3-demo:bootRun` |
| `boot3-postgres-demo` | PostgreSQL (Docker) | 8082 | `./gradlew :examples:boot3-postgres-demo:bootRun` |
| `boot4-demo` | H2 (in-memory) | 8081 | `./gradlew :examples:boot4-demo:bootRun` |
| `boot4-postgres-demo` | PostgreSQL (Docker) | 8083 | `./gradlew :examples:boot4-postgres-demo:bootRun` |

Each demo exposes a REST API with 14 endpoints covering all query patterns. Postman collections are available in `examples/`:

- `Specification-Repository-Demo.postman_collection.json` (H2 demos)
- `Specification-Repository-Postgres-Demo.postman_collection.json` (PostgreSQL demos)

## Configuration

Dependency management is aligned with the Spring Boot BOM -- no explicit version overrides are needed for Spring-managed dependencies.

The starters provide empty `@AutoConfiguration` classes. The key configuration is wiring `SpecificationRepositoryImpl` as the repository base class via `@EnableSpecificationRepositories` or `@EnableJpaRepositories`.

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
