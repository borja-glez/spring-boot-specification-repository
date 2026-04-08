# Architecture

The project is split into a pure query core and JPA/Spring adapters.

- `specification-repository-core` contains the immutable query plan and fluent builder.
- `specification-repository-jpa` translates the query plan into Spring Data JPA `Specification` objects and manages join/fetch reuse through a metamodel-driven registry.
- Starter modules expose auto-configuration for Spring Boot 3 and 4, including bean-based extension points for operators, value converters, and repository customization.

All library code lives under the `com.borjaglez.specrepository` package hierarchy.

## Design goals

- Builders only build.
- Repositories execute.
- Operators are replaceable.
- Value conversion is customizable.
- Association traversal avoids reflection-heavy entity field inspection.

## Quality

- Spotless (Google Java Format) enforces consistent code style.
- Lombok reduces boilerplate.
- Dependency management is aligned with the Spring Boot BOM.
- 100% JaCoCo coverage is enforced with no exclusions.
