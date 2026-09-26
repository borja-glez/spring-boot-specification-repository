# EXISTS and subqueries

The DSL supports correlated subqueries for two families of filters that cannot be
expressed as navigable join-based predicates:

- `EXISTS` / `NOT EXISTS`
- `IN (subquery)` / `NOT IN (subquery)`

These translate to real SQL subqueries via the JPA Criteria API, so the outer
query is not polluted by extra joins or row duplication.

## Why not just join?

A plain `where` on a collection path (`orders.status`, or `tags` for an
`@ElementCollection Set<String>`) joins the collection, and several conditions
on it share that join so that they test the same element. Since 0.4.0 the
library already writes the subquery for the two cases a shared join gets wrong:

- a negative operator (`NOT_EQUALS`, `NOT_IN`, `NOT_CONTAINS`) becomes
  `NOT EXISTS` of the positive operator: `where("orders.status", NOT_EQUALS,
  "CANCELLED")` means "has no cancelled order";
- the same path repeated in an AND becomes one `EXISTS` per condition:
  `where("tags", EQUALS, "a").where("tags", EQUALS, "b")` means "has tag `a`
  and has tag `b`".

Conditions on different attributes of the same element
(`orders.status` + `orders.total`) and the alternatives of an OR group keep
sharing the join, and grouped and projected queries keep every condition on
it. The full rules are in the README section
[Collections and Shared Joins](../README.md#collections-and-shared-joins).

Write the subquery yourself when you need something else:

1. A body that combines several conditions on one element in a way the rules
   above do not, such as "has no order that is both cancelled and over 100",
   or "has a tag other than `a`".
2. A filter on entities that are not mapped as an association from the outer
   root.

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

Only client conditions are validated. A subquery added as a server condition,
through a builder derived from a plan (`plan.toBuilder()` or
`repository.query(plan)`), is not checked against the policy.

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
