package com.borjaglez.specrepository.core;

import static org.apiguardian.api.API.Status.MAINTAINED;

import java.util.Objects;

import org.apiguardian.api.API;

@API(status = MAINTAINED, since = "1.0.0")
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
