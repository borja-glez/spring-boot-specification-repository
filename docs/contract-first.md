# Contract-first: using the HTTP filters from an OpenAPI contract

This guide is for APIs that are written as an OpenAPI document first, with the controllers
implementing interfaces generated from it (OpenAPI Generator, for example). It shows how to describe
the `filter`, `orFilter` and `sort` parameters in the contract, and how to turn what the generated
interface hands you into a `QueryPlan`.

Read [security.md](security.md) first: the field allow-list is still mandatory here.

## 1. Contract-first or code-first

| | Code-first | Contract-first |
|---|---|---|
| Source of truth | the controller (`@FilterableQuery`) | the OpenAPI document |
| Parameters bound by | `QueryPlanArgumentResolver` | the generated interface, then your code |
| Allow-list declared in | the annotation | your code, mirrored in the contract |
| OpenAPI document | generated from the annotation (springdoc integration, milestone 1.1.0) | written by you |

Choose code-first when the Java code is the source of truth. Choose contract-first when the contract
is reviewed, published or shared before the code exists, or when other teams generate clients from
it. In contract-first the annotation and its argument resolver are not involved: you call
`HttpFilterParser` yourself.

## 2. Referencing the fragment

The `specification-repository-http` jar ships a reusable fragment,
`META-INF/specrepo/openapi/specrepo-filters.yaml`, with the parameters `SpecrepoFilter`,
`SpecrepoOrFilter` and `SpecrepoSort`, the schemas `SpecrepoFilterOperator` and
`SpecrepoInvalidFilterProblem`, and the response `SpecrepoInvalidFilter`. Copy it next to your
contract so that tooling can resolve the `$ref`. With Gradle:

```kotlin
val specrepoOpenApi by configurations.creating
dependencies {
    specrepoOpenApi("com.borjaglez.specrepository:specification-repository-http:<version>") { isTransitive = false }
}

val extractSpecrepoFragment by tasks.registering(Copy::class) {
    from(zipTree(specrepoOpenApi.singleFile)) { include("META-INF/specrepo/openapi/specrepo-filters.yaml") }
    into(layout.projectDirectory.dir("src/main/resources/api"))
    eachFile { path = name }
    includeEmptyDirs = false
}
```

`<version>` is the library version you use. Make the generator task depend on
`extractSpecrepoFragment` so the file exists when the contract is resolved.

Then reference the components from an endpoint:

```yaml
paths:
  /products:
    get:
      operationId: searchProducts
      x-specrepo-filterable-fields: [name, status, category.name]
      parameters:
        - $ref: './specrepo-filters.yaml#/components/parameters/SpecrepoFilter'
        - $ref: './specrepo-filters.yaml#/components/parameters/SpecrepoOrFilter'
        - $ref: './specrepo-filters.yaml#/components/parameters/SpecrepoSort'
      responses:
        '200':
          description: The matching products.
        '400':
          $ref: './specrepo-filters.yaml#/components/responses/SpecrepoInvalidFilter'
```

The parameter names in the fragment are the defaults of `HttpFilterParserConfiguration`. If you
rename them with `specrepository.http.*`, rename them in the contract as well.

## 3. Declaring the allow-list in the contract

The generic `field:operator:value` syntax accepts any field, so narrow it per endpoint. Two ways,
which you can combine:

- Give the items a `pattern` that lists the fields that endpoint allows. Define the parameter
  inline (or in your own components) instead of referencing `SpecrepoFilter` as is:

  ```yaml
  - name: filter
    in: query
    style: form
    explode: true
    schema:
      type: array
      items:
        type: string
        pattern: '^(name|status|category\.name):[a-z]+(:.*)?$'
  ```

- Declare `x-specrepo-filterable-fields` on the operation, as in the example above. It is
  documentation for contract tooling (linters, generated docs); the library does not read it.

Neither is a security control. A client can ignore the contract, so the server must enforce the
list itself, which is what the next section does. Review the contract and the Java list together so
they do not drift.

## 4. The controller

The generated interface gives you the parameters as `List<String>`, and `null` when the client did
not send an optional one. Build the parameter map tolerating `null` (`Map.of` throws on a `null`
value) and call `HttpFilterParser#toQueryPlan(Class, Map, AllowedFieldsPolicy, Set)`:

```java
@Component
class ProductSearch {

  private static final AllowedFieldsPolicy POLICY =
      AllowedFieldsPolicy.of(
          Set.of("name", "status", "category.name"), Set.of("name", "createdAt"));

  private final HttpFilterParser parser;

  ProductSearch(HttpFilterParser parser) {
    this.parser = parser;
  }

  // A generated interface passes null for an optional list that the client did not send.
  QueryPlan<Product> toPlan(List<String> filter, List<String> orFilter, List<String> sort) {
    Map<String, List<String>> params = new LinkedHashMap<>();
    if (filter != null) {
      params.put("filter", filter);
    }
    if (orFilter != null) {
      params.put("orFilter", orFilter);
    }
    if (sort != null) {
      params.put("sort", sort);
    }
    QueryPlan<Product> plan = parser.toQueryPlan(Product.class, params, POLICY, Set.of("name"));
    // The parser does not check the fields: fail now, with a 400, instead of when the query runs.
    POLICY.validate(plan);
    return plan;
  }
}
```

This code is compiled and run by `ContractFirstGuideExampleTest`.

`HttpFilterParser` is an auto-configured bean (`HttpFilterAutoConfiguration`, active in a servlet web
application) built from the `specrepository.http.*` properties, so inject it rather than calling
`new HttpFilterParser()`: that way the operator, size and length limits you configured apply.

The parser does not check the fields. `toQueryPlan` stores the policy in the plan, and the plan is
checked again when the query runs, but calling `AllowedFieldsPolicy#validate(plan)` right away, as
`toPlan` does, fails before any work is done. A field outside the lists throws
`DisallowedFieldException`; a malformed filter throws `HttpFilterSyntaxException` or
`HttpUnknownOperatorException`.

Server conditions (ownership, tenant, soft delete) are never client input. Add them to the plan with
`repository.query(plan).where(...)`; the policy does not apply to them:

```java
@RestController
class ProductsController implements ProductsApi {

  private final ProductSearch search;
  private final ProductRepository repository; // extends SpecificationRepository<Product, Long>

  ProductsController(ProductSearch search, ProductRepository repository) {
    this.search = search;
    this.repository = repository;
  }

  @Override
  public ResponseEntity<List<ProductDto>> searchProducts(
      List<String> filter, List<String> orFilter, List<String> sort) {
    QueryPlan<Product> plan = search.toPlan(filter, orFilter, sort);
    List<Product> products =
        repository.query(plan).where("tenantId", FilterOperator.EQ, currentTenantId()).findAll();
    return ResponseEntity.ok(products.stream().map(ProductDto::from).toList());
  }
}
```

`ProductsApi`, `ProductRepository`, `ProductDto` and `currentTenantId()` are yours. This controller
is a sketch and is not compiled by the test, which has no JPA repository; the tenant field must not
be in the allow-list.

Pagination works as in the code-first case: add the paging parameters to the contract and pass a
`Pageable` to `findAll(Pageable)` or `findSlice(Pageable)`. A sort in the `Pageable` is validated
against the sortable fields of the plan (see [security.md](security.md)).

## 5. Errors

`HttpFilterProblemDetailsExceptionHandler` is registered by default (unless
`specrepository.http.problem-details.enabled=false`). It is a `@RestControllerAdvice`, so it answers
the client errors with `400` and a `ProblemDetail` whoever called the parser: a generated
interface's controller behaves like one with `@FilterableQuery`. Spring MVC also matches the cause
of an exception, so a library exception that surfaces wrapped from deeper layers, for example while
the query runs (a repository proxy wraps it in `InvalidDataAccessApiUsageException`), is still a
`400`. The body is what `SpecrepoInvalidFilterProblem` describes, with a `field` property when the
error names a field. See [HTTP status of each exception](security.md#http-status-of-each-exception).

## 6. Hexagonal architectures

If the domain must not depend on the library, pass the raw parameters inwards as a value of your
own (a record with the three lists, `null` allowed) and let the persistence adapter call
`HttpFilterParser` and the repository. The allow-list then lives in the adapter, next to the entity
fields, and the REST adapter only copies the generated parameters into the record.

## 7. Limitations

- The `|` separator of `in`, `notin` and `between`, and the `;` separator of `orFilter`, have no
  escape character: a value cannot contain them.
- An empty value cannot be expressed.
- Contract patterns validate the syntax only; they do not know which operators suit which type.
