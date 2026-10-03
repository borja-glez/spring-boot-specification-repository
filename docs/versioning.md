# Versioning and Support Policy

This document describes what the version number of Spring Boot Specification Repository promises,
how features are deprecated and removed, and which versions of Java, Spring Boot, Spring Data JPA,
Hibernate and databases are supported. It applies from 1.0.0 onwards.

## Semantic versioning

Releases follow [Semantic Versioning 2.0.0](https://semver.org/): `MAJOR.MINOR.PATCH`.

- **Patch** (`1.0.1`): bug and security fixes. No new public API, no change to documented
  behaviour other than the fix.
- **Minor** (`1.1.0`): new features and deprecations, compatible with the previous minor.
- **Major** (`2.0.0`): incompatible changes, including the removal of deprecated elements and of a
  supported Spring Boot line.

All modules (`core`, `jpa`, `http`, `test-support` and both starters) share one version and are
released together. Use the same version for every module you depend on.

### What semantic versioning covers

A compatible release (minor or patch) does not break any of the following:

1. **Java API marked `@API(status = STABLE)` or `@API(status = MAINTAINED)`.** Public types and
   members are annotated with [apiguardian](https://github.com/apiguardian-team/apiguardian)'s
   `@API`. Only these two statuses are covered; see [API status levels](#api-status-levels).
2. **The HTTP query syntax**: the `field:op:value` filter format, the request parameter names, the
   separators, and the operator names.
3. **The HTTP error status codes** returned for invalid or rejected queries.
4. **Documented default behaviour**, such as `@FilterableQuery` denying every field unless field
   lists are declared (deny-by-default). Default limits may only become stricter in a major release.

### What it does not cover

- Elements marked `@API(status = INTERNAL)` or `@API(status = EXPERIMENTAL)`.
- Types and members without an `@API` annotation.
- The text of exception and error messages. Match on exception types and HTTP status codes instead.
- Generated SQL and JPQL. The library may produce different, equivalent queries in any release, as
  long as the results stay the same.
- Behaviour that contradicts the documentation. Fixing such a bug can happen in a patch release.
- Transitive dependency versions, which follow the managed versions of the Spring Boot line in use.

### API status levels

| Status         | Meaning                                                                                                                                                     | Covered by semver |
| -------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------- |
| `STABLE`       | Intended for application code. Changes incompatibly only in a major release, after deprecation.                                                             | Yes               |
| `MAINTAINED`   | Intended for application code, typically extension points and advanced features. Same guarantees as `STABLE` within the current major, but may be promoted to `STABLE` or redesigned in the next major. | Yes               |
| `EXPERIMENTAL` | New feature open for feedback. May change or be removed in any release, including a patch. Use it knowing it can move.                                       | No                |
| `INTERNAL`     | Implementation detail that is public only for technical reasons (for example, shared between modules). Do not use it from application code.               | No                |

An element can be promoted (for example from `EXPERIMENTAL` to `MAINTAINED` or `STABLE`) in a minor
release. Demoting a `STABLE` or `MAINTAINED` element follows the deprecation process.

## Deprecation process

1. An element is deprecated in a **minor** release with `@Deprecated(since = "<version>")` and a
   Javadoc `@deprecated` tag that points to its replacement.
2. The deprecation is listed in [CHANGELOG.md](../CHANGELOG.md) and, when action is needed, in
   [upgrading.md](upgrading.md).
3. The element keeps working until it is removed, which happens **no earlier than the next major**
   release. An element deprecated in 1.x stays available for the whole 1.x line.

The same process applies to the HTTP syntax (a parameter name, separator or operator) and to
documented defaults: the old form keeps working, with a deprecation notice in the documentation,
until the next major.

## Support policy

- **Bug fixes** go to the latest 1.x minor only. Upgrade to the latest minor to receive them.
- **Security fixes** go to the latest minor and to the previous minor for **6 months** after the next
  minor is released. For example, when 1.3.0 is released, 1.2.x receives security fixes for six more
  months.
- **0.x releases** are no longer supported.
- Report vulnerabilities as described in [SECURITY.md](../SECURITY.md).

### Spring Boot lines

The library ships one version with two starters, one per Spring Boot generation:

- `specification-repository-boot3-starter` for Spring Boot 3.5.x (Jackson 2).
- `specification-repository-boot4-starter` for Spring Boot 4.x (Jackson 3).

The Boot 3.5 starter is kept for the **whole 1.x line**, even after the open-source support of
Spring Boot 3.5 ends. Dropping it is a major release (2.0.0).

## Support matrix

Versions tested in CI for the current release line:

| Component        | Boot 3 line (`boot3-starter`) | Boot 4 line (`boot4-starter`) |
| ---------------- | ----------------------------- | ----------------------------- |
| Java             | 21 baseline; tested on 21, 25 | 21 baseline; tested on 21, 25 |
| Spring Boot      | 3.5.x (tested with 3.5.12)    | 4.x (tested with 4.0.4)       |
| Spring Data JPA  | 3.5.x (tested with 3.5.10)    | 4.0.x (tested with 4.0.4)     |
| Hibernate ORM    | 6.6.x (tested with 6.6.44)    | 7.x (tested with 7.2.7)       |
| H2               | Tested (2.4.240)              | Tested (2.4.240)              |
| PostgreSQL       | Tested (17)                   | Tested (17)                   |

Notes:

- The artifacts are compiled for Java 21 (class file version 65). Java 21 is the minimum.
- Spring Data JPA and Hibernate versions are the ones managed by each Spring Boot line. Using the
  versions your Spring Boot release manages is the supported setup; overriding them is at your own
  risk.
- Other databases supported by Hibernate are expected to work but are not tested. Some functions,
  such as accent-insensitive matching, depend on database support.
- CI runs the library test suite on Java 21 and 25, and the Boot 3 and Boot 4 compatibility suites
  (starters and examples) on both Java versions. The PostgreSQL suites use Testcontainers.
