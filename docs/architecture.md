# Architecture

The project is split into a pure query core and JPA/Spring adapters.

- `specification-repository-core` contains the immutable query plan and fluent builder.
- `specification-repository-jpa` translates the query plan into Spring Data JPA `Specification` objects and manages join/fetch reuse through a metamodel-driven registry.
- `specification-repository-http` parses HTTP query parameters into a query plan and, in Spring MVC, resolves `@FilterableQuery` arguments and answers client filter errors with 400 Problem Details.
- Starter modules expose auto-configuration for Spring Boot 3 and 4, including bean-based extension points for operators, value converters, and repository customization.
- `specification-repository-bom` aligns the versions of the published modules.

All library code lives under the `com.borjaglez.specrepository` package hierarchy.

## From an HTTP request to SQL

<p align="center"><img src="assets/diagrams/query-pipeline.svg" alt="Query pipeline: an HTTP request is parsed and bounded by HttpFilterParser (specrepository.http limits and allowed operators), becomes a QueryPlan carrying an AllowedFieldsPolicy (validated at once by the @FilterableQuery argument resolver, otherwise when the query runs), gets server conditions from repository.query(plan).where(...), is turned into a Specification when the query runs, and then into SQL with bound parameters; a request rejected by the parser, the allow-list or the Specification is answered with a 400 Problem Details response" width="100%"></p>

The `http` module parses and bounds the client input, the `core` plan carries the field allow-list,
server conditions are added in code, and the `jpa` module checks the plan again while it builds the
`Specification`. Every client error is answered with a 400 before any SQL runs. See
[security.md](security.md) for the limits and the status of each exception, and
[contract-first.md](contract-first.md) for APIs written as an OpenAPI document first:

<p align="center"><img src="assets/diagrams/contract-first.svg" alt="Contract-first flow: at build time the specrepo-filters.yaml fragment from the specification-repository-http jar is copied next to your OpenAPI contract, which references its parameters and its SpecrepoInvalidFilter 400 response, and OpenAPI Generator turns the contract into the ProductsApi interface; at run time ProductsController implements it and calls your ProductSearch component, which passes the filter, orFilter and sort lists to HttpFilterParser.toQueryPlan (the parser stores the AllowedFieldsPolicy in the plan but checks no field) and then calls POLICY.validate(plan) itself; the plan goes to repository.query(plan) with a tenant server condition and is checked again when the query runs; a disallowed field is answered with a 400 Problem Details response" width="100%"></p>

## Design goals

- Builders only build.
- Repositories execute.
- Operators are replaceable.
- Value conversion is customizable.
- Association traversal avoids reflection-heavy entity field inspection.

## API stability levels

Every public top-level type of `core`, `jpa`, `http` and both starters carries
[`@API`](https://github.com/apiguardian-team/apiguardian) (`org.apiguardian.api.API`) with
`since = "1.0.0"`. Nested types and members share the level of the type that declares them unless
they carry their own annotation. The levels mean:

| Level | Promise | Types |
|-------|---------|-------|
| `STABLE` | No incompatible change before 2.0.0. | The DSL builders (`SpecificationQueryBuilder`, `QueryPlanBuilder`, `ProjectedQueryPlanBuilder`, `ConditionGroupBuilder`, `SubqueryBuilder`), `QueryPlan`, `AllowedFieldsPolicy`, `Operators`, `FilterOperator`, `GroupedRow`, the enums, the exceptions, `SpecificationRepository`, `SpecificationExecutableQuery`, `ProjectedSpecificationExecutableQuery`, `@EnableSpecificationRepositories`, `HttpFilterParser`, `HttpFilterParserConfiguration`, `@FilterableQuery`. |
| `MAINTAINED` | No incompatible change for at least the next minor release of the current major version; any change is listed in `docs/upgrading.md`. | The extension SPI in `jpa.spi` (`OperatorHandler`, `OperatorContext`, `ValueConverter`, `SpecificationRepositoryCustomizer`), `SpecificationRepositoryConfiguration`, and the model records read from a plan (`PredicateCondition`, `GroupCondition`, `SubqueryCondition`, `HavingCondition`, `JoinInstruction`, `FetchInstruction`, `FieldSelection`, `AggregateSelection`, `CorrelationPair`, `QueryLock`, the sealed `QueryCondition` and `Selection`, and `ParsedHttpQuery`). |
| `INTERNAL` | No promise: may change in any release. Public only because another package of the library uses it. | `jpa.support` (except `SpecificationRepositoryConfiguration`), `SpecificationRepositoryImpl`, `SpecificationRepositoryFactoryBean`, `QueryPlanArgumentResolver`, `HttpFilterAutoConfiguration`, the starters' `SpecificationRepositoryAutoConfiguration`, and `QueryPlan.withoutFetches()`. |

The model records are `MAINTAINED` rather than `STABLE` because a record cannot gain a component
without changing its canonical constructor. `QueryPlan` is a final class without a public
constructor for the same reason: a new setting only adds an accessor and a builder method.

`apiguardian-api` is a `compileOnlyApi` dependency, as in JUnit 5, so applications do not get it at
runtime. A `PublicApiAnnotationTest` in each library module fails the build when a public top-level
type has no `@API` annotation.

## Quality

- Spotless (Google Java Format) enforces consistent code style.
- Lombok reduces boilerplate.
- Dependency management is aligned with the Spring Boot BOM.
- 100% JaCoCo coverage is enforced with no exclusions.
