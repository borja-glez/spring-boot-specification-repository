package com.borjaglez.specrepository.core;

import static org.apiguardian.api.API.Status.MAINTAINED;

import java.util.Objects;

import org.apiguardian.api.API;

@API(status = MAINTAINED, since = "1.0.0")
public record HavingCondition(
    AggregateFunction function, String field, FilterOperator operator, Object value) {
  public HavingCondition {
    Objects.requireNonNull(function, "function must not be null");
    Objects.requireNonNull(field, "field must not be null");
    Objects.requireNonNull(operator, "operator must not be null");
  }
}
