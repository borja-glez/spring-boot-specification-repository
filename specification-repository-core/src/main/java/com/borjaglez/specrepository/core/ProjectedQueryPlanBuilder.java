package com.borjaglez.specrepository.core;

public class ProjectedQueryPlanBuilder<T, P> {
  private final QueryPlanBuilder<T> delegate;

  protected ProjectedQueryPlanBuilder(QueryPlanBuilder<T> delegate) {
    this.delegate = delegate;
  }

  public QueryPlan<T> build() {
    return delegate.build();
  }
}
