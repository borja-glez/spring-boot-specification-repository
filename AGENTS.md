# AGENTS.md

## Purpose
This repository is a Gradle multi-module Java project for a Spring Data JPA specification repository library plus demo applications.
Use this file as the default operating guide for coding agents working in this repo.

## Repository Shape
- Root build: `build.gradle.kts`
- Module list: `settings.gradle.kts`
- Shared build logic: `build-logic/src/main/kotlin/*.gradle.kts`
- Core library: `specification-repository-core`
- JPA implementation: `specification-repository-jpa`
- Spring Boot starters: `specification-repository-boot3-starter`, `specification-repository-boot4-starter`
- Test utilities: `specification-repository-test-support`
- Demo apps: `examples/boot3-demo`, `examples/boot3-postgres-demo`, `examples/boot4-demo`, `examples/boot4-postgres-demo`

## Environment
- Java toolchain is pinned to Java 21.
- Gradle wrapper is present: use `./gradlew`, never assume a globally installed Gradle.
- CI runs `./gradlew quality` on Ubuntu with Temurin 21, in a matrix that runs the tests on Java 21 and Java 25.
- `-PtestJavaVersion=<n>` runs every `Test` task on a Java `<n>` toolchain launcher; compilation stays on Java 21 with `release = 21`.
- Gradle configuration cache, parallelism, and build cache are enabled in `gradle.properties`.

## Source Of Truth
- Build and verification behavior comes from `build.gradle.kts` and `build-logic/src/main/kotlin/`.
- Formatting rules come from Spotless in `specification-java-conventions.gradle.kts` and `specification-boot4-application-conventions.gradle.kts`.
- Testing style comes from the existing JUnit 5 and AssertJ tests in `specification-repository-core` and `specification-repository-jpa`.

## Build Commands
Use the wrapper from the repository root.
- Full CI-equivalent verification: `./gradlew quality`
- Full build: `./gradlew build`
- Assemble without full verification: `./gradlew assemble`
- Aggregate library coverage verification: `./gradlew coverage`
- Inspect tasks: `./gradlew tasks --all`

## Module-Scoped Commands
Prefer module-scoped commands while iterating.
- Build one module: `./gradlew :specification-repository-core:build`
- Run one module test suite: `./gradlew :specification-repository-jpa:test`
- Run one module checks: `./gradlew :specification-repository-core:check`
- Run one module coverage gate: `./gradlew :specification-repository-jpa:jacocoTestCoverageVerification`

Important: for library modules, `check` depends on `jacocoTestCoverageVerification`.

## Single-Test Commands
Prefer targeting a module and then a test selector.
- Single test class:
  `./gradlew :specification-repository-core:test --tests "com.borjaglez.specrepository.core.QueryPlanBuilderTest"`
- Single test method:
  `./gradlew :specification-repository-core:test --tests "com.borjaglez.specrepository.core.QueryPlanBuilderTest.shouldBuildImmutableQueryPlan"`
- Another module example:
  `./gradlew :specification-repository-jpa:test --tests "com.borjaglez.specrepository.jpa.support.PathResolverTest"`
- Integration test example:
  `./gradlew :specification-repository-jpa:test --tests "com.borjaglez.specrepository.jpa.it.SpecificationRepositoryIntegrationTest"`

If you do not know the exact test name, search `src/test/java` first.

## Formatting And Lint Commands
There ARE root aggregate tasks for Spotless.
- Format all modules: `./gradlew spotlessApplyAll`
- Check formatting all modules: `./gradlew spotlessCheckAll`
- Format one module: `./gradlew :specification-repository-core:spotlessApply`
- Check formatting in one module: `./gradlew :specification-repository-jpa:spotlessCheck`
- Format Boot 4 demo code: `./gradlew :examples:boot4-demo:spotlessApply`
- Check Boot 4 Postgres demo formatting: `./gradlew :examples:boot4-postgres-demo:spotlessCheck`

Formatting is enforced with:
- `googleJavaFormat()`
- `removeUnusedImports()`
- `trimTrailingWhitespace()`
- `endWithNewline()`
- import order: `java`, `javax`, `jakarta`, `org`, `com`, `io`

## Running Demo Applications
Verified task availability:
- Boot 3 demo: `./gradlew :examples:boot3-demo:bootRun`
- Boot 3 Postgres demo: `./gradlew :examples:boot3-postgres-demo:bootRun`

Boot 4 example modules are present, but the verified task list does not expose `bootRun` for them. Do not assume it exists without checking first.

## Test Stack
- JUnit 5 via `useJUnitPlatform()`
- AssertJ for assertions
- Mockito JUnit Jupiter in library convention plugins
- Spring Boot test slices in starter and integration modules
- H2 for in-memory JPA tests
- Testcontainers PostgreSQL for container-backed tests

## Coverage Expectations
- Library modules enforce 100% line coverage.
- Library modules also enforce 100% branch coverage.
- Do not add partial tests and expect CI to pass later.
- When changing logic in library modules, add or update tests in the same turn.

## Code Style
- Language: Java 21
- Indentation and wrapping: follow Spotless plus Google Java Format, not personal preference.
- Keep imports explicit; do not add wildcard imports.
- Keep import blocks ordered exactly as configured by Spotless.
- Keep files UTF-8.
- End every file with a newline.
- Prefer minimal, direct implementations over abstraction-heavy designs.

## Naming Conventions
- Packages are lowercase: `com.borjaglez.specrepository...`
- Classes, records, enums, and annotations use PascalCase.
- Methods and fields use camelCase.
- Test methods use descriptive `should...` or behavior-focused names.
- Constants, if introduced, should use UPPER_SNAKE_CASE.
- Repository interfaces follow `SomethingRepository`.
- Builder/helper types use explicit names such as `QueryPlanBuilder`, `ConditionGroupBuilder`, `PathResolver`.

## Type And API Design Guidelines
- Prefer records for immutable value carriers and DTO-like structures when the existing code already models them that way.
- Prefer `final` utility classes with a private constructor for factory-only helpers.
- Keep generics explicit where they communicate the domain API clearly.
- Return immutable snapshots from builders using `List.copyOf(...)` when exposing accumulated state.
- Favor small public APIs with fluent chaining when extending the existing builder DSL.
- Every public top-level type in `core`, `jpa`, `http` and the starters carries `@API(status = STABLE | MAINTAINED | INTERNAL, since = "<version>")` from `org.apiguardian.api`; `PublicApiAnnotationTest` in each module fails without it. The levels are described in `docs/architecture.md`.
- `QueryPlan` has no public constructor: create plans through the builders or `toBuilder()`, and add new settings as an accessor plus a builder method.

## Dependency Injection And Framework Style
- Prefer constructor injection.
- Avoid field injection in production code.
- In tests, field injection may be used sparingly when Spring test wiring makes it simpler.
- Keep Spring annotations close to the class declaration and avoid unnecessary meta-annotation layering.

## Nullability And Validation
- Validate required inputs with `Objects.requireNonNull(...)` and a precise message.
- Use `IllegalArgumentException` for invalid caller input.
- Use `IllegalStateException` for impossible or unsupported runtime states.
- Preserve existing error message style: short, direct, and specific.

## Error Handling
- Fail fast on invalid builder inputs and unsupported JPA metadata paths.
- Do not swallow exceptions.
- Do not introduce custom exception hierarchies unless the repository already needs a domain-specific contract.
- Prefer straightforward exceptions over defensive indirection.

## Collections And Control Flow
- Prefer `List` over concrete collection types in public APIs.
- Use `ArrayList` internally when mutability is needed during construction.
- Prefer simple loops or streams based on readability, not style points.
- Keep branching shallow when possible.
- Extract a helper only when it removes real duplication or clarifies a complex block.

## Testing Guidelines
- Keep tests close to the changed module.
- Use AssertJ assertions consistently.
- Test observable behavior, not implementation trivia.
- Add regression coverage for bug fixes.
- For builder and operator logic, test both happy paths and invalid inputs.
- For JPA path/join behavior, prefer focused unit tests first, then integration coverage when persistence behavior matters.
- Preserve the existing behavior-oriented naming and structure used in current tests.

## Domain Conventions Visible In This Repo
- The core module models query instructions as records and sealed interfaces.
- The JPA module translates query plans into Criteria API predicates.
- Nested property paths use dot notation such as `profile.city` or `category.name`.
- Query composition is intentionally fluent and chainable.
- Example applications use plain getters/setters and explicit constructors rather than Lombok-generated models.

## When Editing Build Logic
- Check `build-logic/src/main/kotlin/` before changing module build files.
- Keep conventions centralized there instead of duplicating plugin configuration across modules.
- If you change formatting or coverage behavior, update this file too.

## Cursor And Copilot Rules
- No `.cursor/rules/` directory was found.
- No `.cursorrules` file was found.
- No `.github/copilot-instructions.md` file was found.
- Do not invent editor-specific rules that are not present in the repo.

## Agent Workflow Expectations
- Start with the smallest correct change.
- Run the narrowest relevant test command first.
- Run formatting for the touched Spotless-managed module before finishing.
- If library logic changed, finish with the relevant module `check` or repo `quality` when feasible.
- Do not claim a command exists unless you verified it from the build or task list.

## Working on an issue

Issues are written as work orders: Context, Reproduction, Expected behaviour, Where to look, Proposed approach, Acceptance criteria, Compatibility, Dependencies, Out of scope. Follow them; they are the spec.

1. **Check the labels first.** `agent-ready` means implement it. `needs-design` means do **not** implement unless the issue body has a `### Decision` section recorded by the maintainer; otherwise stop and report the open questions.
2. **Check `Dependencies`.** If an issue listed there is still open, stop and report it instead of re-implementing it.
3. **Branch** from the latest `main`: `fix/<issue-number>-<short-slug>` for bugs, `feat/<issue-number>-<short-slug>` for features, `docs/<issue-number>-<short-slug>` for documentation.
4. **Regression test first.** Write the test named in the acceptance criteria, see it fail for the reason described, then fix. Keep the test in the same change.
5. **Both starters.** When auto-configuration or starter code changes, apply and test it in the Boot 3 and the Boot 4 starter.
6. **Verify before pushing:** `./gradlew spotlessApplyAll`, then `./gradlew quality` (tests, 100% line and branch coverage, Spotless). If a starter or an example changed, also run `./gradlew verifyBoot3Compatibility verifyBoot4Compatibility`.
7. **Commits:** Conventional Commits with a scope (`fix(http): ...`, `feat(kafka): ...`). The subject says what changes; the body, when needed, says why and how. A breaking change uses `!` in the subject and a `BREAKING CHANGE:` footer.
8. **Never** add `Co-Authored-By` lines, "Generated with ..." footers, session links, or any mention of Claude, AI, a model or a session in commits, pull request titles or pull request descriptions.
9. **Pull request:** title equal to the commit subject; body from `.github/pull_request_template.md` with `Closes #<issue>` and a short explanation of how it was solved; same labels as the issue. Do not merge it: the maintainer squash-merges once CI is green.
10. **Stay in scope.** Do not touch `CHANGELOG.md` (generated from commits), the version in `gradle.properties`, the workflows, or code outside the issue's `Where to look` unless the acceptance criteria need it.
11. **When blocked** (ambiguous behaviour, an acceptance criterion that cannot be met, a failing test you cannot explain), open the pull request as a draft and describe the blocker instead of guessing.
