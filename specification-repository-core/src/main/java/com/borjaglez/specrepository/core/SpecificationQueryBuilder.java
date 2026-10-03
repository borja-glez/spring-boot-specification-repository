package com.borjaglez.specrepository.core;

import static org.apiguardian.api.API.Status.STABLE;

import org.apiguardian.api.API;

@API(status = STABLE, since = "1.0.0")
public final class SpecificationQueryBuilder {
  private SpecificationQueryBuilder() {}

  public static <T> QueryPlanBuilder<T> forEntity(Class<T> entityType) {
    return new QueryPlanBuilder<>(entityType);
  }

  /**
   * A builder derived from {@code plan}: the conditions added through it are server conditions.
   * Same as {@link QueryPlan#toBuilder()}.
   */
  public static <T> QueryPlanBuilder<T> from(QueryPlan<T> plan) {
    return plan.toBuilder();
  }
}
