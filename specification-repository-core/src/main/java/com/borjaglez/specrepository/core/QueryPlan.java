package com.borjaglez.specrepository.core;

import java.util.List;

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

  public boolean hasSelections() {
    return !selections.isEmpty();
  }

  public boolean hasAggregates() {
    return selections.stream().anyMatch(AggregateSelection.class::isInstance);
  }
}
