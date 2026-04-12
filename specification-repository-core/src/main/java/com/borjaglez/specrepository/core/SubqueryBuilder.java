package com.borjaglez.specrepository.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class SubqueryBuilder<S> {
  private final ConditionGroupBuilder<S> root = new ConditionGroupBuilder<>(LogicalOperator.AND);
  private final List<CorrelationPair> correlations = new ArrayList<>();

  public SubqueryBuilder<S> where(String field, FilterOperator operator, Object value) {
    root.where(field, operator, value);
    return this;
  }

  public SubqueryBuilder<S> where(
      String field,
      FilterOperator operator,
      Object value,
      boolean ignoreCase,
      boolean includeNulls) {
    root.where(field, operator, value, ignoreCase, includeNulls);
    return this;
  }

  public SubqueryBuilder<S> and(Consumer<ConditionGroupBuilder<S>> nested) {
    root.and(nested);
    return this;
  }

  public SubqueryBuilder<S> or(Consumer<ConditionGroupBuilder<S>> nested) {
    root.or(nested);
    return this;
  }

  public SubqueryBuilder<S> correlate(String outerField, String innerField) {
    correlations.add(new CorrelationPair(outerField, innerField));
    return this;
  }

  GroupCondition buildCondition() {
    return root.build();
  }

  List<CorrelationPair> correlations() {
    return List.copyOf(correlations);
  }

  static <S> SubqueryCondition buildAssociation(
      SubqueryKind kind, String associationPath, Consumer<SubqueryBuilder<S>> body) {
    Objects.requireNonNull(associationPath, "associationPath must not be null");
    Objects.requireNonNull(body, "body must not be null");
    SubqueryBuilder<S> builder = new SubqueryBuilder<>();
    body.accept(builder);
    return new SubqueryCondition(
        kind,
        CorrelationMode.ASSOCIATION,
        associationPath,
        null,
        List.of(),
        null,
        null,
        builder.buildCondition());
  }

  static <S> SubqueryCondition buildEntity(
      SubqueryKind kind, Class<S> subEntity, Consumer<SubqueryBuilder<S>> body) {
    Objects.requireNonNull(subEntity, "subEntity must not be null");
    Objects.requireNonNull(body, "body must not be null");
    SubqueryBuilder<S> builder = new SubqueryBuilder<>();
    body.accept(builder);
    return new SubqueryCondition(
        kind,
        CorrelationMode.ENTITY,
        null,
        subEntity,
        builder.correlations(),
        null,
        null,
        builder.buildCondition());
  }

  static <S> SubqueryCondition buildIn(
      SubqueryKind kind,
      String outerField,
      Class<S> subEntity,
      String subSelectField,
      Consumer<SubqueryBuilder<S>> body) {
    Objects.requireNonNull(outerField, "outerField must not be null");
    Objects.requireNonNull(subEntity, "subEntity must not be null");
    Objects.requireNonNull(subSelectField, "subSelectField must not be null");
    Objects.requireNonNull(body, "body must not be null");
    SubqueryBuilder<S> builder = new SubqueryBuilder<>();
    body.accept(builder);
    return new SubqueryCondition(
        kind,
        CorrelationMode.ENTITY,
        null,
        subEntity,
        builder.correlations(),
        outerField,
        subSelectField,
        builder.buildCondition());
  }
}
