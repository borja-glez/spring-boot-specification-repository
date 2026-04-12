package com.borjaglez.specrepository.core;

import java.util.Objects;

public record CorrelationPair(String outerField, String innerField) {
  public CorrelationPair {
    Objects.requireNonNull(outerField, "outerField must not be null");
    Objects.requireNonNull(innerField, "innerField must not be null");
  }
}
