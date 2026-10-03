package com.borjaglez.specrepository.core;

import static org.apiguardian.api.API.Status.STABLE;

import org.apiguardian.api.API;

@API(status = STABLE, since = "1.0.0")
public class ProjectedQueryPlanBuilder<T, P> {
  private final QueryPlanBuilder<T> delegate;

  protected ProjectedQueryPlanBuilder(QueryPlanBuilder<T> delegate) {
    this.delegate = delegate;
  }

  public QueryPlan<T> build() {
    return delegate.build();
  }
}
