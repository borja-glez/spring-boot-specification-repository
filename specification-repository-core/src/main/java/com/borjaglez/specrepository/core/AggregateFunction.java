package com.borjaglez.specrepository.core;

public enum AggregateFunction {
  SUM,
  AVG,
  MIN,
  MAX,
  COUNT,
  /** Counts distinct values; use it on the root id when to-many joins repeat root rows. */
  COUNT_DISTINCT
}
