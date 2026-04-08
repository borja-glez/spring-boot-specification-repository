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
