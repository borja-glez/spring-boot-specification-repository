# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Breaking changes are marked with **BREAKING:**; [docs/upgrading.md](docs/upgrading.md)
explains how to migrate.
## [0.4.0] - 2026-09-26

### Added
- **BREAKING:** Add pessimistic row locks to query plans (#113) (418b4a9)
- **BREAKING:** Evaluate negated and repeated conditions on a collection path with EXISTS (#111) (09886ae)
- **BREAKING:** Make @FilterableQuery without field lists deny by default (#109) (f9d3bc0)
- **BREAKING:** Separate client filters from server conditions and derive plans (#106) (ec9b0ec)
- Add caseInsensitiveFields to @FilterableQuery (#103) (4b96a17)
- Support @FilterableQuery as a meta-annotation on composed annotations (#101) (24ba859)

### Documentation
- Prepare the 0.4.0 release (#115) (1cd6bfd)
- Document which parts of a query plan the AllowedFieldsPolicy covers (#112) (1ad2956)

### Fixed
- **BREAKING:** Validate the Pageable sort against the plan's AllowedFieldsPolicy (#110) (9e46965)
- **BREAKING:** Stop returning selected values as entities without selectInto (#105) (529e76d)
- **BREAKING:** Report unconvertible filter values as InvalidFilterValueException (#104) (e6ac78a)
- Throw InvalidFilterException for unknown operators and fields (#102) (9d6f4fb)

### Testing
- Read the unconvertible having tests with findRows (#108) (183d0ba)

### Build
- Keep the release workflow's own commits out of the changelog (#117) (2623c67)
- Publish only the checksum files Maven Central requires (#100) (ef4f2dc)
## [0.3.1] - 2026-09-26

### Documentation
- Document native image hints for selectInto projections (#88) (3b59ae0)
- State that groupBy fields must be selected to appear in rows (#87) (5b1bb81)
- Describe how to work on an issue (#85) (af08c3d)

### Fixed
- Apply innerFetch when counting so Page totals match the rows (#97) (6479404)
- Fetch an element collection of basic values (#98) (7bccbcc)
- Page root ids first when a paginated plan fetches a collection (#94) (1c10abc)
- Support exists and notExists on collections of basic values (#92) (438d891)
- Escape % and _ in contains, notcontains, startswith and endswith (#91) (b055eb0)
- Register HttpFilterAutoConfiguration for the @WebMvcTest slice (#90) (afa2e16)
- Treat filter operators case-insensitively (#89) (d1fc72d)

### Testing
- Run the JPA integration suites on PostgreSQL as well as H2 (#93) (1177d71)

### Ci
- Update workflow actions to their Node 24 releases (#84) (1b1ed43)
## [0.3.0] - 2026-09-26

### Added
- Add COUNT_DISTINCT aggregate (#45) (5a9b792)

### Fixed
- Return and count each root once when a filter crosses a collection (#62) (c470fd4)
- Fetch an association that is also joined (#60) (dc9388c)
- Share association joins between filters, projections and grouping (#58) (2de3cf2)
- Treat eq/neq with a null value as is null / is not null (#56) (6e429ce)
- Ignore accents in the search term of case-insensitive filters (#54) (8f71007)
- Pass basePackages of @EnableSpecificationRepositories on to Spring Data (#51) (c318b4f)
- Make findOne read a single row (#49) (d263200)
- Join basic element collections in filter paths (#47) (bd5089f)
- Route plan-based repository methods to the implementation (#43) (fb481d1)
## [0.2.0] - 2026-05-14

### Miscellaneous
- Release workflow update readme update (7801094)
## [0.2.0-rc.0] - 2026-04-13

### Added
- Add HAVING and structured grouped results (#25) (#40) (66cebc1)
- Add Slice pagination and document keyset as follow-up (#39) (4d8dc5b)
- Add EXISTS and subquery support to DSL (#38) (b919105)
- Add HTTP filter parser module (#22) (#37) (f3add7e)
- Add field whitelist for filtering and sorting (#36) (b95f229)
- Expose specification repository extension points (#35) (4f5fc4c)
- Support DTO and record projections (#34) (05ed8a2)
- Add aggregate query support to DSL and demos (#31) (77d6c62)

### Miscellaneous
- Migrate Maven Central group ID to com.borjaglez.specrepository (#33) (#41) (9791be3)
- Release workflow update readme update (74a9dd5)
## [0.1.0-rc.1] - 2026-04-06

### Miscellaneous
- Pre releases set changelog as body (9619807)
## [0.1.0-rc.0] - 2026-04-06

### Miscellaneous
- Pre releases set changelog as body (c6ad1ad)
- Update actions versions (e8eb513)
## [0.1.0-beta.2] - 2026-04-05

### Added
- Specification repositories now with autoregistrar support (9cfb2dc)

### Miscellaneous
- Add new examples with the new features prs #26 #27 #28 (bd7921e)
- Remove dependabot.yml (8f15af6)

### Testing
- Add starters tests (7b7a825)

### Doc
- Change license shield url username (0ab7873)
## [0.1.0-beta.1] - 2026-04-04

### Added
- Add BETWEEN operator support (#28) (f709b59)
- Support grouped counts in JPA repository (#27) (484890e)
- Execute select projections in JPA repository (#26) (949e1c7)
- Initial version deployment (7f7c532)

### Documentation
- Expand advanced query examples (#29) (9a1b0d5)

### Miscellaneous
- On release update README with new version (b000f23)
- Apply spotless (f761366)
- Add spotless check in quality task (3c5389c)
- Add executable permissions to gradlew (03791c7)
