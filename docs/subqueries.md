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

### Conditions on the same collection path share one join

Every `where` on the same collection path tests the *same* joined element,
whether the path is a collection association (`orders.status`) or a collection
of basic values (`tags`, an `@ElementCollection Set<String>`):

- `where("tags", EQUALS, "a").where("tags", EQUALS, "b")` matches nothing: no
  single tag is both `a` and `b`.
- `where("tags", NOT_EQUALS, "a")` means "has some tag other than `a`", not
  "does not have tag `a`".

Use one `exists` / `notExists` per condition for per-element semantics:

```java
// has tag a AND has tag b
customers.query()
    .<String>exists("tags", sub -> sub.where("value", Operators.EQUALS, "a"))
    .<String>exists("tags", sub -> sub.where("value", Operators.EQUALS, "b"))
    .findAll();

// does not have tag a
customers.query()
    .<String>notExists("tags", sub -> sub.where("value", Operators.EQUALS, "a"))
    .findAll();
```

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

### Collections of basic values

The association path can also end in a collection of basic values, such as an
`@ElementCollection Set<String> tags`. The subquery joins the collection from
the correlated outer root, and its root is the element itself, which has no
attributes: the body refers to the element with the field `value`. Any other
field is rejected with an `IllegalArgumentException`.

```java
customers.query()
    .<String>exists("tags", sub -> sub.where("value", Operators.EQUALS, "vip"))
    .findAll();

customers.query()
    .<String>notExists("tags", sub -> sub.where("value", Operators.STARTS_WITH, "beta"))
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
- Nested subqueries are supported by the translator and are expressible in
  the DSL via grouped conditions passed to subquery-body `and(...)` /
  `or(...)`, whose inner builder exposes `exists` / `inSubquery`. Prefer a
  flatter structure when it reads more clearly, but nested forms are
  available.
- Aggregate / scalar subqueries (e.g. `(SELECT MAX(...) FROM ...)`) are not
  exposed through the DSL.

## GraalVM native image

Subquery translation does not use reflection; path resolution is done via
the JPA metamodel, which is native-image friendly, so subqueries need no
reflection hints. The library registers no hints of its own; `selectInto(...)`
projections do need one, see
[GraalVM Native Image](../README.md#graalvm-native-image) in the README.
