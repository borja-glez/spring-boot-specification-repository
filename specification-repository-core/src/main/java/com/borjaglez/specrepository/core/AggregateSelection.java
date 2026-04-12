package com.borjaglez.specrepository.core;

import java.util.Objects;

public record AggregateSelection(AggregateFunction function, String field, String alias)
    implements Selection {
  public AggregateSelection {
    Objects.requireNonNull(function, "function must not be null");
    Objects.requireNonNull(field, "field must not be null");
    if (alias != null && alias.isBlank()) {
      throw new IllegalArgumentException("alias must not be blank");
    }
  }

  public AggregateSelection(AggregateFunction function, String field) {
    this(function, field, null);
  }

  public String columnName() {
    return alias != null ? alias : function.name() + "_" + field;
  }
}
