package com.borjaglez.specrepository.core;

import java.util.Objects;

public record AggregateSelection(AggregateFunction function, String field) implements Selection {
  public AggregateSelection {
    Objects.requireNonNull(function, "function must not be null");
    Objects.requireNonNull(field, "field must not be null");
  }
}
