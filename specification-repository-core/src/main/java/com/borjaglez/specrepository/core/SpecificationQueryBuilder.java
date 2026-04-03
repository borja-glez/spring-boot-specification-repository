package com.borjaglez.specrepository.core;

public final class SpecificationQueryBuilder {
  private SpecificationQueryBuilder() {}

  public static <T> QueryPlanBuilder<T> forEntity(Class<T> entityType) {
    return new QueryPlanBuilder<>(entityType);
  }
}
