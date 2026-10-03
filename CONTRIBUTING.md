# Contributing

Thank you for your interest in contributing to this project! Every contribution is welcome.

## Getting Started

### Prerequisites

- **Java 21** (Temurin or any compatible distribution)
- **Git**
- **Docker** (optional, needed for the PostgreSQL integration tests)

No additional installation is needed — the project uses the Gradle wrapper (`./gradlew`).

### Building and Testing

```bash
# Run all tests and coverage checks
./gradlew quality

# Run tests for a specific module
./gradlew :specification-repository-core:test
./gradlew :specification-repository-jpa:test

# Run a single test class
./gradlew :specification-repository-core:test --tests "com.borjaglez.specrepository.core.QueryPlanBuilderTest"

# Run a single test method
./gradlew :specification-repository-core:test --tests "com.borjaglez.specrepository.core.QueryPlanBuilderTest.shouldBuildImmutableQueryPlan"
```

### JPA integration tests on H2 and PostgreSQL

The JPA integration suites (`AbstractSpecificationRepositoryIntegrationTest`,
`AbstractExistsAndSubqueryIntegrationTest` in `specification-repository-jpa`) run twice: on the
embedded H2 database (`*IntegrationTest`) and on PostgreSQL 17 through Testcontainers
(`*PostgresIntegrationTest`). Add new database-neutral tests to the abstract suite so they run on
both.

- The PostgreSQL classes need Docker. Without it they are skipped, and the H2 run alone still
  reaches the 100% coverage gate. CI has Docker and runs both.
- All PostgreSQL classes use `@PostgresIntegrationTest`, which shares one container per test JVM
  (with the `unaccent` extension created) and one Spring context.
- A test that cannot run on one database is overridden in that database's subclass with
  `@Test @Disabled("<database>: <reason>")`.

## Code Style

- Formatting is managed by **Spotless** with Google Java Format.
- Run `./gradlew spotlessApplyAll` to auto-format all modules before committing.
- Run `./gradlew :<module>:spotlessApply` to auto-format a single module.
- Imports are ordered: `java`, `javax`, `jakarta`, `org`, `com`, `io`.
- All files must end with a newline and use UTF-8 encoding.

## Commit Conventions

This project uses **Conventional Commits**. Your commit messages should follow this format:

```
<type>(<scope>): <description>

[optional body]
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `ci`, `chore`

Examples:
- `feat(core): add support for nested OR conditions`
- `fix(jpa): resolve path resolution for composite keys`
- `docs: update contributing guide with test commands`

## Branch Naming

Use descriptive branch names prefixed with the type of change:

- `feat/short-description` — new features (snapshots published automatically)
- `fix/short-description` — bug fixes
- `docs/short-description` — documentation only
- `refactor/short-description` — code restructuring
- `release/X.Y.x` — maintenance branch for older versions (e.g., `release/0.1.x`)

## Release Branches

Which released versions receive fixes is defined in the
[versioning and support policy](docs/versioning.md): bug fixes go to the latest minor only, security
fixes also to the previous minor for 6 months. Changes to public API marked `STABLE` or `MAINTAINED`
follow its deprecation process.

When a critical bug needs fixing in an already-released version while `main` has moved forward:

```bash
# Create a maintenance branch from the release tag
git checkout -b release/0.1.x v0.1.0

# Apply the fix, commit, and PR to release/0.1.x
# Once merged, trigger a release from that branch:
#   release.yml → version: "0.1.1", branch: "release/0.1.x"
```

Maintenance branches follow the same CI rules as `main`:
- `ci.yml` runs on PRs
- `snapshot.yml` publishes snapshots on push (e.g., `0.1.1-SNAPSHOT`)
- `release.yml` can publish stable releases from any branch via the `branch` input

## Pull Request Process

1. Fork the repository and create your branch from `main`.
2. Add or update tests for every code change.
3. Run `./gradlew quality` and ensure all checks pass.
4. Format your code with `./gradlew :<module>:spotlessApply`.
5. Open a pull request with a clear description of the change and its motivation.
6. Link any related issues.

## Reporting Issues

- **Bugs**: Use the [bug report template](https://github.com/borja-glez/spring-boot-specification-repository/issues/new?template=bug_report.yml).
- **Features**: Use the [feature request template](https://github.com/borja-glez/spring-boot-specification-repository/issues/new?template=feature_request.yml).

## Documentation

Keep documentation in English. Update relevant docs when changing behavior or adding features.
