package com.borjaglez.specrepository.core;

import static org.apiguardian.api.API.Status.MAINTAINED;

import java.util.Objects;

import org.apiguardian.api.API;

@API(status = MAINTAINED, since = "1.0.0")
public record FieldSelection(String field) implements Selection {
  public FieldSelection {
    Objects.requireNonNull(field, "field must not be null");
  }
}
