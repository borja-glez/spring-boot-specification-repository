package com.borjaglez.specrepository.core;

import static org.apiguardian.api.API.Status.MAINTAINED;

import java.util.Objects;

import org.apiguardian.api.API;

@API(status = MAINTAINED, since = "1.0.0")
public record CorrelationPair(String outerField, String innerField) {
  public CorrelationPair {
    Objects.requireNonNull(outerField, "outerField must not be null");
    Objects.requireNonNull(innerField, "innerField must not be null");
  }
}
