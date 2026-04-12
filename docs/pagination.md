# Pagination

The DSL exposes two pagination contracts on the fluent query, both backed by
Spring Data's `Pageable` argument:

- `findAll(Pageable)` returns `Page<T>` — content + total row count.
- `findSlice(Pageable)` returns `Slice<T>` — content + `hasNext` flag, no
  total count.

Both honor the same sort priority: when the supplied `Pageable` is sorted, its
`Sort` overrides any `sort(...)` set on the builder; otherwise the builder's
sort is used.

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
- DTO and record projections via `selectInto(...)`,
- field projections via `select(...)`,
- aggregate / `groupBy` queries.

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
