package com.borjaglez.specrepository.core;

/**
 * The pessimistic row lock a query takes on the rows it reads, until the transaction ends.
 *
 * @see QueryLock
 */
public enum LockMode {
  /** No lock: the default. */
  NONE,
  /** A shared lock ({@code FOR SHARE} on PostgreSQL): others can read, not change, the rows. */
  PESSIMISTIC_READ,
  /** An exclusive lock ({@code FOR UPDATE}): others can neither lock nor change the rows. */
  PESSIMISTIC_WRITE
}
