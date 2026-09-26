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
the result columns easier to refer to from `having` and `findRows()`.

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
    .findRows();
```

The aliased variants (`sumAs`, `avgAs`, `minAs`, `maxAs`, `countAs`) take an
explicit alias plus the field. The original `sum`, `avg`, `min`, `max`,
`count` overloads still work without an alias; the column name is then
derived as `FUNCTION_field` (for example `SUM_age`).

For full control, use `aggregate(AggregateFunction.SUM, "age", "totalAge")`.

## `findRows()`, `findRow()` and `GroupedRow`

A query with `select(...)` or aggregate selections returns rows, not entities.
Read them with one of the row terminals, or map them into a type with
`selectInto(...)`:

| Terminal | Returns |
|---|---|
| `findRows()` | `List<GroupedRow>`, one per result row |
| `findRow()` | `Optional<GroupedRow>`, the first row only (the database stops there) |
| `findAllGrouped()` | Same as `findRows()` |
| `selectInto(Dto.class).findAll()` (and `findAll(Pageable)`, `findSlice`, `findOne`) | the rows mapped into `Dto` |

The repository exposes the same row reads for a prebuilt plan:
`findRows(QueryPlan)`, `findRow(QueryPlan)` and `findAllGrouped(QueryPlan)`.

The entity terminals (`findAll()`, `findAll(Pageable)`, `findSlice(Pageable)`,
`findOne()`, and the repository `findAll`, `findSlice` and `findOne` methods
that take a `QueryPlan`) reject a plan with selections and no `selectInto(...)`
with an `IllegalStateException` that names these alternatives. Through a
repository proxy, Spring translates it to `InvalidDataAccessApiUsageException`.
Rows are never handed out typed as the entity.

A single aggregate without `groupBy(...)` returns exactly one row:

```java
Number totalAge = (Number) customerRepository.query()
    .where("status", Operators.IS_NOT_NULL, null)
    .sumAs("totalAge", "age")
    .findRow()
    .map(row -> row.get("totalAge"))
    .orElse(null);
```

A `GroupedRow` carries both the column names (from the selections) and their
values, and supports lookup by index or by name:

```java
GroupedRow active = rows.get(0);
String status      = (String) active.get("status");
Number totalAge    = (Number) active.get("totalAge");
Number averageAge  = (Number) active.get("averageAge");
Long   customers   = (Long)   active.get("customers");
```

Notes:

- `groupBy(...)` does not add its fields to the result: a row only contains
  the declared selections. Select the group fields with `select(...)`, in the
  order you want the columns, or the rows will not say which group they
  belong to.
- Column order matches the order in which selections were declared.
- For `FieldSelection` (`select(...)`) the column name is the field name.
- For `AggregateSelection` the column name is the alias if provided, or
  `FUNCTION_field` (for example `COUNT_id`) otherwise.
- `GroupedRow.values()` returns a defensive copy of the underlying array.
- Calling `findRows()`, `findRow()` or `findAllGrouped()` on a query without
  any selection throws `IllegalStateException` -- there is nothing meaningful
  to project.
- A row terminal has no pagination: to page selected fields or aggregates, map
  them with `selectInto(...)` and use `findAll(Pageable)` or
  `findSlice(Pageable)` (see [pagination.md](pagination.md)).

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
    .select("customerId")
    .sumAs("revenue", "amount")
    .countAs("orders", "id")
    .having(AggregateFunction.SUM, "amount", Operators.GREATER_THAN, 1_000)
    .having(AggregateFunction.COUNT, "id", Operators.GREATER_THAN_OR_EQUAL, 5)
    .findRows();

// each row: [customerId, revenue, orders]
Object customerId = bigSpenders.get(0).get("customerId");
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
    .findRows();

for (GroupedRow row : rows) {
    System.out.println(
        row.get("status") + " -> "
        + row.get("revenue") + " across "
        + row.get("orders") + " orders");
}
```
