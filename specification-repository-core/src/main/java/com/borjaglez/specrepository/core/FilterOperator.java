package com.borjaglez.specrepository.core;

import static org.apiguardian.api.API.Status.STABLE;

import java.util.Objects;

import org.apiguardian.api.API;

@API(status = STABLE, since = "1.0.0")
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
