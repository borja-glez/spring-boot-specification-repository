package com.borjaglez.specrepository.core;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Sort;

public record QueryPlan<T>(
    Class<T> entityType,
    GroupCondition rootCondition,
    List<JoinInstruction> joins,
    List<FetchInstruction> fetches,
    List<String> projections,
    List<Selection> selections,
    Class<?> projectionType,
    List<String> groupBy,
    Sort sort,
    boolean distinct,
    AllowedFieldsPolicy allowedFieldsPolicy) {

  public QueryPlan {
    Objects.requireNonNull(allowedFieldsPolicy, "allowedFieldsPolicy must not be null");
  }

  public QueryPlan(
      Class<T> entityType,
      GroupCondition rootCondition,
      List<JoinInstruction> joins,
      List<FetchInstruction> fetches,
      List<String> projections,
      List<Selection> selections,
      Class<?> projectionType,
      List<String> groupBy,
      Sort sort,
      boolean distinct) {
    this(
        entityType,
        rootCondition,
        joins,
        fetches,
        projections,
        selections,
        projectionType,
        groupBy,
        sort,
        distinct,
        AllowedFieldsPolicy.allowAll());
  }

  public boolean hasSelections() {
    return !selections.isEmpty();
  }

  public boolean hasAggregates() {
    return selections.stream().anyMatch(AggregateSelection.class::isInstance);
  }
}
