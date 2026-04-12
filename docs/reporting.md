# Reporting and analytical queries

This guide covers the reporting-oriented features of the query DSL: aliased
aggregate selections, the `having` clause for filtering grouped results, and
the `GroupedRow` value object for column lookup by alias.

These features build on top of `groupBy(...)` and the existing aggregate
functions (`sum`, `avg`, `min`, `max`, `count`). They require no extra
configuration: any `SpecificationRepository` exposes them through the same
fluent query builder.

## Multiple aggregates in a single query

Several aggregate selections can be combined in the same query. This was
already supported through the projection pipeline; the new aliasing API makes
the result columns easier to refer to from `having` and `findAllGrouped()`.

```java
List<GroupedRow> rows = customerRepository.query()
    .where("status", Operators.IS_NOT_NULL, null)
    .groupBy("status")
    .sort(Sort.by("status"))
    .select("status")
    .sumAs("totalAge", "age")
    .avgAs("averageAge", "age")
    .countAs("customers", "id")
    .minAs("youngest", "age")
    .maxAs("oldest", "age")
    .findAllGrouped();
```

The aliased variants (`sumAs`, `avgAs`, `minAs`, `maxAs`, `countAs`) take an
explicit alias plus the field. The original `sum`, `avg`, `min`, `max`,
`count` overloads still work without an alias; the column name is then
derived as `FUNCTION_field` (for example `SUM_age`).

For full control, use `aggregate(AggregateFunction.SUM, "age", "totalAge")`.

## `findAllGrouped()` and `GroupedRow`

`findAllGrouped()` is a terminal method that returns each result row as a
`GroupedRow`. A `GroupedRow` carries both the column names (from the
selections) and their values, and supports lookup by index or by name:

```java
GroupedRow active = rows.get(0);
String status      = (String) active.get("status");
Number totalAge    = (Number) active.get("totalAge");
Number averageAge  = (Number) active.get("averageAge");
Long   customers   = (Long)   active.get("customers");
```

Notes:

- Column order matches the order in which selections were declared.
- For `FieldSelection` (`select(...)`) the column name is the field name.
- For `AggregateSelection` the column name is the alias if provided, or
  `FUNCTION_field` (for example `COUNT_id`) otherwise.
- `GroupedRow.values()` returns a defensive copy of the underlying array.
- Calling `findAllGrouped()` on a query without any selection throws
  `IllegalStateException` -- there is nothing meaningful to project.

If you prefer constructor-based DTOs, the existing `selectInto(MyRecord.class)`
projection still works and is often a better fit when columns are known at
compile time.

## `having` clause

`having(function, field, operator, value)` filters grouped rows after
aggregation. It is the analytical counterpart of `where(...)`: `where`
runs before `groupBy`, `having` runs after. Multiple `having(...)` calls
on the same query are combined with logical AND -- nested groups are not
supported (yet).

```java
List<GroupedRow> bigSpenders = orderRepository.query()
    .groupBy("customerId")
    .sumAs("revenue", "amount")
    .countAs("orders", "id")
    .having(AggregateFunction.SUM, "amount", Operators.GREATER_THAN, 1_000)
    .having(AggregateFunction.COUNT, "id", Operators.GREATER_THAN_OR_EQUAL, 5)
    .findAllGrouped();
```

### Supported operators

The HAVING clause supports the comparison operators that map cleanly onto
aggregate expressions:

| Operator | Notes |
|---|---|
| `Operators.EQUALS` / `NOT_EQUALS` | Equality and inequality. |
| `Operators.GREATER_THAN` / `GREATER_THAN_OR_EQUAL` | Numeric / comparable comparisons. |
| `Operators.LESS_THAN` / `LESS_THAN_OR_EQUAL` | Numeric / comparable comparisons. |
| `Operators.BETWEEN` | Value must be a 2-element `Iterable` (lower, upper). |
| `Operators.IS_NULL` / `IS_NOT_NULL` | Useful when an aggregate may be `NULL`. |

Pattern operators (`LIKE`, `CONTAINS`, ...) and the collection operators
(`IN`, `IS_EMPTY`, ...) are not allowed in the `having` clause and result in
`IllegalArgumentException` at execution time. The same exception is raised
for any custom or unrecognized operator.

### Validation

Calling `having(...)` without a preceding `groupBy(...)` is rejected by the
builder with `IllegalStateException("having requires at least one groupBy field")`.
This mirrors SQL semantics and catches misuse early.

When an `AllowedFieldsPolicy` is attached to the query, the field referenced
by every `having(...)` clause is validated against the *filterable* fields
set, exactly like `where(...)` clauses. Use this to expose `having` to
external API callers safely.

## Putting it together

```java
AllowedFieldsPolicy policy = AllowedFieldsPolicy.of(
    Set.of("status", "amount", "id"),
    Set.of("status"));

List<GroupedRow> rows = orderRepository.query()
    .allowedFields(policy)
    .where("status", Operators.IS_NOT_NULL, null)
    .groupBy("status")
    .sort(Sort.by("status"))
    .select("status")
    .sumAs("revenue", "amount")
    .countAs("orders", "id")
    .having(AggregateFunction.SUM, "amount", Operators.GREATER_THAN, 100)
    .findAllGrouped();

for (GroupedRow row : rows) {
    System.out.println(
        row.get("status") + " -> "
        + row.get("revenue") + " across "
        + row.get("orders") + " orders");
}
```
