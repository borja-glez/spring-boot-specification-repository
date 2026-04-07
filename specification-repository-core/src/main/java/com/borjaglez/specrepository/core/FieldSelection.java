package com.borjaglez.specrepository.core;

import java.util.Objects;

public record FieldSelection(String field) implements Selection {
  public FieldSelection {
    Objects.requireNonNull(field, "field must not be null");
  }
}
