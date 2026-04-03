package com.borjaglez.specrepository.core;

import java.util.Objects;

public record FilterOperator(String value) {
  public FilterOperator {
    Objects.requireNonNull(value, "value must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
  }

  public static FilterOperator of(String value) {
    return new FilterOperator(value);
  }
}
