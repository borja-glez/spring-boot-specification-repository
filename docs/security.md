# Exposing the HTTP filter API safely

The `specification-repository-http` module turns request parameters into a `QueryPlan`. This page
lists what that gives a client, what the library protects on its own, and what the application has
to do. It ends with a complete controller that follows every point.

To report a vulnerability, see [SECURITY.md](../SECURITY.md).

## 1. Threat model

An endpoint that takes a `@FilterableQuery QueryPlan<T>` and a `Pageable` lets **any client** choose
these request parameters:

| Parameter | Read by | What the client controls |
|---|---|---|
| `filter` (repeatable) | `HttpFilterParser` | field, operator and value of AND-combined conditions |
| `orFilter` (repeatable) | `HttpFilterParser` | OR groups of conditions |
| `sort` (repeatable) | `HttpFilterParser` **and** Spring Data's `Pageable` resolver | sort fields and directions |
| `page`, `size` | Spring Data's `Pageable` resolver | offset and number of rows |

The parameter names can be changed with the `specrepository.http.*` properties and
`spring.data.web.*`, but the threat is the same: everything above is untrusted input. A hostile
client can try to:

- read data it must not see, by filtering on a hidden field (`passwordHash`, `tenantId`) or widening
  a server rule with an `orFilter`;
- learn the value of a hidden field without returning it, by filtering or sorting on it and watching
  the result (an oracle);
- inject SQL or JPQL through a field name or a value;
- turn a `contains` search into a full wildcard match with `%` or `_`;
- exhaust the database or the application with many conditions, huge `IN` lists, very long values,
  huge pages, deep offsets, or unindexed filters and sorts on large tables.

Everything else in the plan (selections, grouping, aggregates, `having`, joins, fetches, locks,
subqueries) has **no HTTP syntax**: it is chosen in code and trusted.

## 2. What the library guarantees

### Field whitelist, deny by default

- `@FilterableQuery` turns `filterableFields` and `sortableFields` into an `AllowedFieldsPolicy`.
  A field outside the list is rejected with `DisallowedFieldException`, by exact name: allowing
  `category.name` does not allow `category.secret` or `category`.
- A parameter that declares no list accepts **no** filter and **no** sort. A request that filters
  or sorts through an undeclared list is rejected with `UndeclaredFieldListException`. Accepting
  every field requires `allowAllFields = true`, which cannot be combined with the lists.
- The `filter`, `orFilter` and `sort` parameters are checked while the argument is resolved, before
  the handler runs. The plan keeps the policy and is checked again when the query runs.
- Field names must match `[a-zA-Z][a-zA-Z0-9_]*` with optional dotted segments, so
  `../secret`, quotes, spaces and functions are rejected as `HttpFilterSyntaxException` before the
  whitelist is even consulted.
- Fields are resolved through the JPA metamodel. A name the entity does not have is an
  `InvalidFilterException`, never a string spliced into a query.

### Sort and `Pageable` sort validation

- The `sort` request parameter goes through the same policy (sortable fields).
- The sort of a sorted `Pageable` is also client input. `findAll(plan, pageable)`,
  `findSlice(plan, pageable)`, their projected variants and the fluent `findAll(pageable)` /
  `findSlice(pageable)` check each of its properties against the plan's sortable fields and throw
  `DisallowedFieldException` (usage `sorting`) before any SQL runs. Passing the controller's
  `Pageable` unchanged is therefore safe.
- A sort set in code on a derived builder (`sort(...)`, `sortedByDefault(...)`) is checked too, so
  pick a default sort among the sortable fields.

### Server conditions cannot be widened

`repository.query(plan)` (or `plan.toBuilder()`) returns a builder seeded with the client plan.
Conditions added there (`where`, `and`, `or`, `exists`, ...) are **server conditions**:

- they are combined with the client conditions as `(client conditions) AND (server conditions)`, so
  no `filter` or `orFilter` can widen them;
- they are not checked against the policy, so they can use fields the client may not touch
  (`ownerId`, `tenantId`, `deleted`);
- the derived plan keeps the client policy, so the client part is still validated.

The HTTP syntax has no `having`: a client can never add a `having` condition, only code can.

### `LIKE` escaping

The default `contains`, `notcontains`, `startswith` and `endswith` handlers escape `\`, `%` and
`_` in the value and render `LIKE ... ESCAPE '\'`, so `name:contains:%` matches a literal `%`
instead of every row. This also holds with `ignoreCase`.

### Values are bound, not concatenated

Queries are built with the JPA Criteria API. Field names become metamodel paths and filter values
are passed to the criteria builder as values: there is no string concatenation into JPQL or SQL,
and with Hibernate the values of the default operators are sent as JDBC bind parameters.

One exception: with `ignoreCase` (`caseInsensitiveFields`) and the default handlers, the search
term of `eq`, `neq`, `contains`, `notcontains`, `startswith` and `endswith` is passed through
`CriteriaBuilder.literal(...)`, which Hibernate renders as an SQL string literal with its quotes
escaped, not as a bind parameter. It is not concatenated by the library, but if your database
treats backslashes inside string literals as escapes (for example MySQL without
`NO_BACKSLASH_ESCAPES`), prefer not to expose `caseInsensitiveFields` until this is changed.

### Input limits

`HttpFilterParser` rejects a request above any of these limits with `HttpFilterSyntaxException`,
before any SQL runs. The message names the limit and, for the value limits, the field; an oversized
value is not echoed back.

| Limit | Property | Default | What it bounds |
|---|---|---|---|
| `maxFilters` | `specrepository.http.max-filters` | 20 | conditions in `filter` plus every `orFilter` group |
| `maxSortFields` | `specrepository.http.max-sort-fields` | 5 | `sort` parameters |
| `maxValuesPerFilter` | `specrepository.http.max-values-per-filter` | 100 | values of an `in` / `notin` filter (`between` always takes exactly 2) |
| `maxValueLength` | `specrepository.http.max-value-length` | 1000 | characters of a single value, each value of a multi-value filter on its own |
| `allowedOperators` | `specrepository.http.allowed-operators` | empty: every registered operator | operators a client may use |

Every limit must be at least 1; a lower value fails application startup. A user-defined
`HttpFilterParser` bean replaces the auto-configured one, and the properties are then ignored.

Because Spring Data's `Pageable` resolver reads the same `sort` parameter by default, `maxSortFields`
also bounds the `Pageable` sort. If you rename one of the two parameters
(`specrepository.http.sort-param` or `spring.data.web.sort.sort-parameter`), the `Pageable` sort is
still whitelisted but no longer counted.

With `allowed-operators` empty, every registered operator, **including custom ones**, is reachable
over HTTP. List the operators an API needs (for example `[eq, neq, in, contains, gte, lte]`) when
you register operators that should stay internal or that are expensive.

### HTTP status of each exception

The module registers no exception handler. With Spring MVC and Spring Boot defaults:

| Exception | Raised when | Raised by | Default status |
|---|---|---|---|
| `UndeclaredFieldListException` | filter or sort through a usage without a declared list | argument resolver | **400** (`@ResponseStatus(BAD_REQUEST)`) |
| `HttpFilterSyntaxException` | malformed expression, invalid field name, a limit exceeded | argument resolver | **500** |
| `HttpUnknownOperatorException` | operator outside `allowed-operators` | argument resolver | **500** |
| `DisallowedFieldException` | field outside a declared list (`filter`, `orFilter`, `sort`) | argument resolver | **500** |
| `DisallowedFieldException` | `Pageable` sort outside the sortable fields | repository, when the query runs | **500** |
| `InvalidFilterException` | unknown field, or operator with no registered handler | repository, when the query runs | **500** |
| `InvalidFilterValueException` | value that cannot be converted (`price:gt:abc`) | repository, when the query runs | **500** |
| `IllegalStateException` | `allowAllFields = true` combined with field lists (a server bug) | argument resolver | 500 |

All of them extend `IllegalArgumentException`, which Spring MVC does not map to a status. The ones
raised when the query runs arrive wrapped in Spring's `InvalidDataAccessApiUsageException` when the
call goes through the repository proxy (for example `repository.findAll(plan, pageable)`).

**The application must add an exception handler** that maps them to 400, or clients get a 500 for
every invalid request. `@ExceptionHandler` methods also match the cause of an exception, so a
handler for `DisallowedFieldException` and `InvalidFilterException` covers the wrapped case as
well, as long as no handler in the same advice matches the wrapper itself (for example an
`@ExceptionHandler(Exception.class)`). See `FilterErrorHandler` in the example below.

## 3. What the application must do

- **Map the client errors to 400.** Add a `@RestControllerAdvice` like the one below. Do not
  render the messages as HTML: they can contain parts of the request.
- **Cap the page size.** Set `spring.data.web.pageable.max-page-size`. Spring Data's default is
  **2000** rows per page, and `?size=` above the cap is silently lowered to it. Consider also
  bounding `page` (deep `OFFSET`s scan and discard every skipped row), or use keyset pagination
  for feeds (see [pagination.md](pagination.md)).
- **Keep ownership and tenancy on the server.** "Only my orders", the tenant, soft-delete and
  visibility rules are server conditions added with `repository.query(plan).where(...)`. Never
  expect the client to send them as a `filter`, and never list those fields in `filterableFields`
  or `sortableFields`: a sortable or filterable hidden field is an oracle even when the response
  does not include it.
- **Whitelist narrowly.** Declare only the fields the screen needs. Avoid `allowAllFields = true`
  on public endpoints, and avoid paths through associations the endpoint should not reveal. Return
  a DTO rather than the entity, and decide in code which associations are fetched and serialized.
- **Never pass request input to the parts of the plan that are trusted**: `select(...)`,
  `groupBy(...)`, `having(...)`, `leftJoin(...)`, `leftFetch(...)`, `lock(...)`. If an endpoint
  offers such a choice, map it from a fixed set of options.
- **Index the filterable and sortable columns.** Every field in `filterableFields` and
  `sortableFields` is a `WHERE` or `ORDER BY` a client can trigger at will. Without an index, each
  request can scan the table. `contains` and `endswith` (`LIKE '%term%'`) cannot use a B-tree index;
  on PostgreSQL use a trigram (`pg_trgm`) index, or only expose `startswith` / `eq` on large
  tables.
- **Prefer `findSlice` on large tables.** `findAll(Pageable)` runs a `COUNT(*)` with the same
  filters on every request, which on a large table can cost more than the page itself. `findSlice`
  fetches `size + 1` rows and skips the count. If a total is required, compute or cache it
  separately.
- **Set query timeouts.** A filter combination the indexes do not cover can still be slow. Bound
  every query, for example with `@Transactional(readOnly = true, timeout = 5)` (Spring applies the
  transaction timeout to the JPA queries created in it) or a database statement timeout (PostgreSQL
  `statement_timeout`, MySQL `max_execution_time`).
- **Restrict operators and tune the limits** with `specrepository.http.*` when the defaults are
  wider than the API needs, for example a lower `max-value-length` for a search box.
- **Rate-limit the endpoint** like any other search API; the limits above bound one request, not
  how many a client sends.

## 4. A complete, minimal secure controller

The example below is part of the Boot 3 demo application and is tested by
[`SecureProductControllerTest`](../examples/boot3-demo/src/test/java/com/borjaglez/specrepository/examples/boot3/SecureProductControllerTest.java).
The same code works on Spring Boot 4.

[`SecureProductController`](../examples/boot3-demo/src/main/java/com/borjaglez/specrepository/examples/boot3/secure/SecureProductController.java):

```java
@RestController
@RequestMapping("/api/catalog/products")
public class SecureProductController {

  private final ProductRepository productRepository;

  public SecureProductController(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  @GetMapping
  @Transactional(readOnly = true, timeout = 5)
  public ProductSlice search(
      @FilterableQuery(
              value = Product.class,
              filterableFields = {"name", "price", "category.name"},
              sortableFields = {"name", "price"})
          QueryPlan<Product> query,
      Pageable pageable) {
    Slice<Product> slice =
        productRepository
            .query(query)
            .where("status", Operators.EQUALS, ProductStatus.ACTIVE)
            .leftFetch("category")
            .sortedByDefault(Sort.by("name"))
            .findSlice(pageable);
    return new ProductSlice(
        slice.getContent().stream().map(ProductView::of).toList(), slice.hasNext());
  }

  public record ProductSlice(List<ProductView> content, boolean hasNext) {}

  public record ProductView(Long id, String name, BigDecimal price, String category) {
    static ProductView of(Product product) {
      return new ProductView(
          product.getId(), product.getName(), product.getPrice(), product.getCategory().getName());
    }
  }
}
```

- Deny by default: the client filters by `name`, `price` and `category.name` and sorts by `name`
  and `price`, nothing else.
- `status = ACTIVE` is a server condition: `?filter=status:eq:DISCONTINUED` is rejected, and
  `?orFilter=name:eq:Nokia 3310;name:eq:Clean Code` only returns the active product. A tenant or
  owner check goes in the same place.
- The `Pageable` is passed unchanged: its sort is checked against the sortable fields.
- `findSlice` skips the `COUNT(*)`, the transaction bounds the query time, and the response is a
  DTO.

[`FilterErrorHandler`](../examples/boot3-demo/src/main/java/com/borjaglez/specrepository/examples/boot3/secure/FilterErrorHandler.java):

```java
@RestControllerAdvice
public class FilterErrorHandler {

  @ExceptionHandler({
    HttpFilterSyntaxException.class,
    HttpUnknownOperatorException.class,
    DisallowedFieldException.class,
    InvalidFilterException.class
  })
  public ProblemDetail invalidFilter(IllegalArgumentException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }
}
```

`DisallowedFieldException` also covers `UndeclaredFieldListException`, and `InvalidFilterException`
covers `InvalidFilterValueException`. The demo limits the advice to `SecureProductController`
(`assignableTypes`) so that its other endpoints keep their behaviour; an application usually
applies it to every controller.

`application.yml`:

```yaml
spring:
  data:
    web:
      pageable:
        max-page-size: 100

# Optional: the defaults of the HTTP parser, tighten them as needed.
specrepository:
  http:
    max-filters: 20
    max-sort-fields: 5
    max-values-per-filter: 100
    max-value-length: 1000
    allowed-operators: [eq, neq, contains, startswith, gt, gte, lt, lte, between, in]
```

The demo sets only `max-page-size` and keeps the parser defaults.
