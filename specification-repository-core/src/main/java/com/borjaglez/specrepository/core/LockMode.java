package com.borjaglez.specrepository.core;

import static org.apiguardian.api.API.Status.STABLE;

import org.apiguardian.api.API;

/**
 * The pessimistic row lock a query takes on the rows it reads, until the transaction ends.
 *
 * @see QueryLock
 */
@API(status = STABLE, since = "1.0.0")
public enum LockMode {
  /** No lock: the default. */
  NONE,
  /** A shared lock ({@code FOR SHARE} on PostgreSQL): others can read, not change, the rows. */
  PESSIMISTIC_READ,
  /** An exclusive lock ({@code FOR UPDATE}): others can neither lock nor change the rows. */
  PESSIMISTIC_WRITE
}
