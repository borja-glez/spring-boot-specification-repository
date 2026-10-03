package com.borjaglez.specrepository.core;

import static org.apiguardian.api.API.Status.STABLE;

import org.apiguardian.api.API;

@API(status = STABLE, since = "1.0.0")
public enum AggregateFunction {
  SUM,
  AVG,
  MIN,
  MAX,
  COUNT,
  /** Counts distinct values; use it on the root id when to-many joins repeat root rows. */
  COUNT_DISTINCT
}
