# Extensibility

The library is designed around extension contracts in the `com.borjaglez.specrepository` package:

- `OperatorHandler`: add or replace operators.
- `ValueConverter`: override value parsing for custom types.
- `SpecificationRepository`: use the fluent DSL or execute an externally built `QueryPlan`.

Example custom operator use cases:

- PostgreSQL JSONB comparisons
- case-insensitive locale-specific matching
- tenant-aware predicates
