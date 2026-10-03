package com.borjaglez.specrepository.core;

import static org.apiguardian.api.API.Status.STABLE;

import org.apiguardian.api.API;

/**
 * What a locking query does with rows another transaction has already locked.
 *
 * @see QueryLock
 */
@API(status = STABLE, since = "1.0.0")
public enum LockWait {
  /** Waits until the other transaction releases them: the default. */
  WAIT,
  /** Fails at once ({@code NOWAIT}). */
  NOWAIT,
  /**
   * Leaves them out of the result ({@code SKIP LOCKED}), so that several workers can read disjoint
   * batches of the same queue.
   */
  SKIP_LOCKED
}
