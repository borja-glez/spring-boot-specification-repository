# Pagination

The DSL exposes two pagination contracts on the fluent query, both backed by
Spring Data's `Pageable` argument:

- `findAll(Pageable)` returns `Page<T>` — content + total row count.
- `findSlice(Pageable)` returns `Slice<T>` — content + `hasNext` flag, no
  total count.

Both honor the same sort priority: when the supplied `Pageable` is sorted, its
`Sort` overrides any `sort(...)` set on the builder; otherwise the builder's
sort is used. A sort set with `sort(...)` or `sortedByDefault(...)` on a
builder derived from a client plan (`repository.query(plan)`) is server input
and is not checked against the policy; the client sort it keeps is.

The `Pageable` sort usually comes from the client (the `sort` request
parameter), so it is checked against the plan's `AllowedFieldsPolicy` before it
overrides the plan sort: a property outside the policy's sortable fields throws
`DisallowedFieldException` (usage `sorting`) before any SQL runs. This applies
to `findAll(plan, pageable)`, `findSlice(plan, pageable)`, their projected
variants and the fluent `findAll(Pageable)` / `findSlice(Pageable)`. With
`AllowedFieldsPolicy.allowAll()` (the default) any `Pageable` sort is accepted.
In a Spring MVC application with the HTTP module, that exception is answered
with 400 Problem Details by default (see
[docs/security.md](security.md#http-status-of-each-exception)).

## `findAll(Pageable)` — counted pagination

```java
Page<Product> page = productRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .sort(Sort.by("name"))
    .findAll(PageRequest.of(0, 20));

page.getTotalElements();  // 12_345
page.getTotalPages();     // 618
page.hasNext();           // true
```

Translates to **two SQL queries**:

1. `SELECT ... FROM ... WHERE ... ORDER BY ... LIMIT 20 OFFSET 0`
2. `SELECT COUNT(*) FROM ... WHERE ...`

Use it when callers need to render "page X of Y" controls or report a total
count to the user.

## `findSlice(Pageable)` — windowed pagination

```java
Slice<Product> slice = productRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .sort(Sort.by("name"))
    .findSlice(PageRequest.of(0, 20));

slice.getContent();   // up to 20 elements
slice.hasNext();      // true if more rows exist
```

Translates to **one SQL query** that fetches `pageSize + 1` rows:

```sql
SELECT ... FROM ... WHERE ... ORDER BY ... LIMIT 21 OFFSET 0
```

If 21 rows come back, `hasNext` is `true` and the 21st row is dropped before
the `Slice` is returned. If fewer than `pageSize + 1` come back, `hasNext` is
`false`.

Use it when:

- The result set is large and the `COUNT(*)` is expensive (full table scans,
  joins on big tables, group-by counts).
- The UI only needs "next" / "previous" controls, not a total page count.
- A REST endpoint streams pages to a client that already knows how to detect
  the end via `hasNext`.

`findSlice` works for the same shapes as `findAll(Pageable)`:

- entity results,
- DTO and record projections via `selectInto(...)`, including selected fields
  and aggregate / `groupBy` queries.

A query with `select(...)` or aggregate selections and no `selectInto(...)`
returns rows, not entities, so `findAll(Pageable)` and `findSlice(Pageable)`
reject it with `IllegalStateException`. Page such a query by mapping it with
`selectInto(...)`:

```java
record StatusCount(String status, Long customers) {}

Page<StatusCount> page = customerRepository.query()
    .groupBy("status")
    .sort(Sort.by("status"))
    .select("status")
    .count("id")
    .selectInto(StatusCount.class)
    .findAll(PageRequest.of(0, 20));
```

## Pages that fetch a collection

A fetch join over a collection (`leftFetch("orders")`) returns one row per
element, so the database cannot cut a page of roots with `LIMIT` / `OFFSET`.
Left to Hibernate, it would read every matching row and paginate in memory
(warning `HHH90003004`), or throw with
`hibernate.query.fail_on_pagination_over_collection_fetch=true`.

When the entity query of `findAll(Pageable)`, `findSlice(Pageable)` or
`findOne()` fetches at least one collection (at any depth, such as
`customer.orders`), the page is read in two steps:

1. **Id query**: the root ids with the plan's conditions, joins and sort, and
   the page's offset and limit (`pageSize + 1` for a slice, `1` for
   `findOne`). It has no fetches; an `innerFetch` becomes an inner join, so it
   still drops the roots without the association. The sort expressions are
   selected next to the id, which keeps `SELECT DISTINCT` valid on PostgreSQL
   when the plan sorts by an association such as `profile.city`.
2. **Entity query**: the roots with the plan's conditions and fetches,
   restricted to `id in (:ids)`, with no offset or limit. The entities are
   returned in the order of the ids.

```sql
SELECT DISTINCT c.id, p.city, c.name FROM customer c
  LEFT JOIN orders o ON ... LEFT JOIN profile p ON ...
  WHERE o.status = ? ORDER BY 2, 3 LIMIT 20 OFFSET 0
SELECT c.*, o.* FROM customer c LEFT JOIN orders o ON ...
  WHERE o.status = ? AND c.id IN (?, ?, ...)
```

An empty id list returns an empty page without the second query. The totals
of `findAll(Pageable)` still come from the count query, and `hasNext` of a
slice from the id query. Plans that fetch only to-one associations, and
`findAll()` without a page, keep a single query.

Notes:

- A sort by a collection path (`orders.total`) returns one id row per
  element: each root is kept once, at its first position, so such a page can
  hold fewer roots than its size.
- Entities with a composite id (`@IdClass` or `@EmbeddedId`) keep the single
  query, which Hibernate paginates in memory (and rejects with
  `fail_on_pagination_over_collection_fetch`).
- A plan with a pessimistic lock (`lock(...)`) cannot fetch a collection
  here: the lock would have to cover the id query too. It fails with an
  `IllegalStateException`; lock the page without the fetch and load the
  collection afterwards, in the same transaction.

## `Page` vs `Slice` — when to use what

| Aspect                       | `findAll(Pageable)` → `Page` | `findSlice(Pageable)` → `Slice` |
|------------------------------|------------------------------|---------------------------------|
| SQL queries                  | 2 (data + count)             | 1 (data only, fetches `pageSize + 1`) |
| Total row count              | yes                          | no                              |
| `hasNext` / `hasPrevious`    | yes                          | yes                             |
| Random access (jump to page) | yes                          | yes (offset-based)              |
| Cost on large tables         | dominated by `COUNT(*)`      | one extra row per page          |
| Best fit                     | UIs with "page X of Y"       | infinite scroll, large exports, count-less APIs |

## Keyset pagination — design note (not yet implemented)

Both `Page` and `Slice` are *offset-based*: behind the scenes JPA issues
`OFFSET n LIMIT m`, which forces the database to scan and discard the first
`n` rows on every request. For large `n` this becomes O(offset) and
dominates the cost of the query, even with `findSlice`.

**Keyset pagination** (a.k.a. seek pagination) avoids the offset entirely by
remembering the last row's sort key from the previous page and translating
"give me the next page" into a compound predicate:

```sql
SELECT ... FROM products
WHERE (created_at, id) > (:lastCreatedAt, :lastId)
ORDER BY created_at, id
LIMIT 21
```

This is O(log n) on a properly indexed `(created_at, id)` and stays constant
as the dataset grows.

### Why it is not implemented yet

A keyset implementation needs more than a Slice constructor:

- A **stable, total ordering** is required. The sort columns must end with a
  unique tiebreaker (typically the primary key); otherwise the comparison can
  skip or duplicate rows.
- A **cursor token** must encode the last row's sort-key tuple in a form the
  caller can persist between requests (typically base64-encoded JSON or a
  signed token).
- The **DSL surface** must let the caller pass that cursor in, and the
  translator must turn it into a compound `(col1, col2, ...) > (val1, val2, ...)`
  predicate. Spring Data Commons does not have a portable abstraction for this
  — it has to be built in this library.
- Some **operator semantics change**: descending sort needs `<` instead of
  `>`, mixed asc/desc needs row constructors that not every JPA dialect
  supports cleanly.

That is a meaningful amount of design and a separate change from the
single-method `Slice` addition. Because of that, keyset pagination is
documented here but **deferred to a follow-up issue**.

### Proposed shape (subject to change)

```java
Slice<Product> slice = productRepository.query()
    .where("status", Operators.EQUALS, "ACTIVE")
    .sort(Sort.by("createdAt", "id"))
    .keysetAfter(previousCursor)   // encoded last-row tuple, may be null
    .findSlice(PageRequest.ofSize(20));

String nextCursor = encodeCursor(slice.getContent().getLast());
```

The terminal would still return a `Slice<T>` so callers do not need a third
return type, and the cursor format would be opaque to the caller. The
follow-up will define cursor encoding, allowed sort shapes, and the
interaction with `AllowedFieldsPolicy`.
