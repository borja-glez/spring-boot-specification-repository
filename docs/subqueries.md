# EXISTS and subqueries

The DSL supports correlated subqueries for two families of filters that cannot be
expressed as navigable join-based predicates:

- `EXISTS` / `NOT EXISTS`
- `IN (subquery)` / `NOT IN (subquery)`

These translate to real SQL subqueries via the JPA Criteria API, so the outer
query is not polluted by extra joins or row duplication.

## Why not just join?

Filtering a collection association with a normal `where` works for trivial
cases, but it:

1. Duplicates outer rows when the association is `@OneToMany` / `@ManyToMany`,
   forcing `distinct()` and breaking stable pagination.
2. Cannot express *negation over a collection* ("customers with no cancelled
   order"): `where("orders.status", NOT_EQUALS, "CANCELLED")` only hides rows
   where the join matched, leaving the customer in the result via other rows.
3. Cannot filter by entities that are not mapped as an association from the
   outer root.

Subqueries solve all three.

## API

All subquery methods live on `ConditionGroupBuilder<T>` (and therefore on
`QueryPlanBuilder<T>` / `SpecificationExecutableQuery<T>`).

### Association-based correlation

Use when the subquery walks an association already mapped on the outer entity.
Correlation is implicit:

```java
customers.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .<Order>exists("orders", sub -> sub.where("total", Operators.GREATER_THAN, 100))
    .findAll();
```

`notExists` mirrors the same shape:

```java
customers.query()
    .<Order>notExists("orders", sub -> sub.where("status", Operators.EQUALS, "CANCELLED"))
    .findAll();
```

### Entity-based correlation

Use when the subquery is over an arbitrary entity class. Correlation is
explicit via one or more `correlate(outerField, innerField)` calls:

```java
customers.query()
    .exists(Order.class, sub -> sub
        .correlate("id", "customer.id")
        .where("status", Operators.EQUALS, "PAID"))
    .findAll();
```

Multiple `correlate` calls are ANDed together, which lets you correlate on
composite keys.

### `IN (subquery)` and `NOT IN (subquery)`

```java
customers.query()
    .inSubquery("id", Order.class, "customer.id",
        sub -> sub.where("vip", Operators.EQUALS, true))
    .findAll();
```

`notInSubquery` is the negation. `outerField` is the outer column to check
membership of, and `subSelectField` is the single column projected from the
subquery entity.

## Composing subqueries with groups

A subquery call participates in the containing group like any other condition,
so it combines naturally with `and` / `or`:

```java
customers.query()
    .or(group -> group
        .where("status", Operators.EQUALS, "INACTIVE")
        .<Order>exists("orders", sub -> sub.where("vip", Operators.EQUALS, true)))
    .findAll();
```

Inside the subquery body you can use the same `where` / `and` / `or`
primitives as the outer builder.

## Validation and `AllowedFieldsPolicy`

When an `AllowedFieldsPolicy` is configured, the outer fields referenced by a
subquery are validated:

- `inSubquery` / `notInSubquery`: the `outerField` must be in the allowed set.
- Entity-based `exists` / `notExists`: the `outerField` of every
  `correlate(...)` pair must be in the allowed set.

Fields referenced *inside* the subquery body (on the sub-entity) are **not**
validated against the outer policy, since they belong to a different entity.
If you need sub-entity validation, enforce it with a separate policy on the
inner repository.

## Limitations

- No `ALL` / `ANY` quantifiers.
- `inSubquery` only supports entity-based correlation and a single projection
  column; multi-column tuples are not supported.
- `inSubquery` / `notInSubquery` do not have an association-based overload.
- Nesting subqueries inside subqueries is supported by the translator, but
  the DSL currently only exposes `where` / `and` / `or` / `correlate` inside
  a subquery body — not further `exists` / `inSubquery`. Use entity-based
  correlation via a flat structure instead.
- Aggregate / scalar subqueries (e.g. `(SELECT MAX(...) FROM ...)`) are not
  exposed through the DSL.

## GraalVM native image

Subquery translation does not use reflection; path resolution is done via
the JPA metamodel, which is native-image friendly. No additional hints are
required beyond what the library already registers.
