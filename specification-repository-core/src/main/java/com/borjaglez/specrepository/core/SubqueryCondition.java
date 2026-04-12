package com.borjaglez.specrepository.core;

import java.util.List;
import java.util.Objects;

public record SubqueryCondition(
    SubqueryKind kind,
    CorrelationMode correlationMode,
    String associationPath,
    Class<?> subEntity,
    List<CorrelationPair> correlations,
    String outerField,
    String subSelectField,
    GroupCondition subCondition)
    implements QueryCondition {

  public SubqueryCondition {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(correlationMode, "correlationMode must not be null");
    Objects.requireNonNull(subCondition, "subCondition must not be null");
    correlations = List.copyOf(correlations == null ? List.of() : correlations);
    if (correlationMode == CorrelationMode.ASSOCIATION) {
      Objects.requireNonNull(associationPath, "associationPath must not be null for ASSOCIATION");
    } else {
      Objects.requireNonNull(subEntity, "subEntity must not be null for ENTITY");
    }
    if (kind == SubqueryKind.IN || kind == SubqueryKind.NOT_IN) {
      Objects.requireNonNull(outerField, "outerField must not be null for IN/NOT_IN");
      Objects.requireNonNull(subSelectField, "subSelectField must not be null for IN/NOT_IN");
    }
  }
}
