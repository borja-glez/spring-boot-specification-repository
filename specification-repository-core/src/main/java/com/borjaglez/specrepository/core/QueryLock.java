package com.borjaglez.specrepository.core;

import java.util.Objects;

/**
 * The pessimistic row lock of a {@link QueryPlan}: its {@link LockMode} and what it does with rows
 * that are already locked ({@link LockWait}). Set with {@link QueryPlanBuilder#lock(LockMode,
 * LockWait)}.
 *
 * <p>The lock applies to the entity query only, never to the count query of a page, and it needs an
 * active transaction.
 */
public record QueryLock(LockMode mode, LockWait lockWait) {

  /** No lock. */
  public static final QueryLock NONE = new QueryLock(LockMode.NONE, LockWait.WAIT);

  public QueryLock {
    Objects.requireNonNull(mode, "mode must not be null");
    Objects.requireNonNull(lockWait, "lockWait must not be null");
    if (mode == LockMode.NONE && lockWait != LockWait.WAIT) {
      throw new IllegalArgumentException(
          "A lock wait other than WAIT requires a lock mode other than NONE");
    }
  }

  /** Whether the query locks the rows it reads. */
  public boolean isLocked() {
    return mode != LockMode.NONE;
  }
}
