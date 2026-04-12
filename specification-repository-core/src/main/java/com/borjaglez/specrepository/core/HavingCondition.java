package com.borjaglez.specrepository.core;

import java.util.Objects;

public record HavingCondition(
    AggregateFunction function, String field, FilterOperator operator, Object value) {
  public HavingCondition {
    Objects.requireNonNull(function, "function must not be null");
    Objects.requireNonNull(field, "field must not be null");
    Objects.requireNonNull(operator, "operator must not be null");
  }
}
