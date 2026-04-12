package com.borjaglez.specrepository.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class ConditionGroupBuilder<T> {
  private final LogicalOperator logicalOperator;
  private final List<QueryCondition> conditions = new ArrayList<>();

  public ConditionGroupBuilder(LogicalOperator logicalOperator) {
    this.logicalOperator =
        Objects.requireNonNull(logicalOperator, "logicalOperator must not be null");
  }

  public ConditionGroupBuilder<T> where(String field, FilterOperator operator, Object value) {
    return where(field, operator, value, false, false);
  }

  public ConditionGroupBuilder<T> where(
      String field,
      FilterOperator operator,
      Object value,
      boolean ignoreCase,
      boolean includeNulls) {
    conditions.add(new PredicateCondition(field, operator, value, ignoreCase, includeNulls));
    return this;
  }

  public ConditionGroupBuilder<T> and(Consumer<ConditionGroupBuilder<T>> nested) {
    return group(LogicalOperator.AND, nested);
  }

  public ConditionGroupBuilder<T> or(Consumer<ConditionGroupBuilder<T>> nested) {
    return group(LogicalOperator.OR, nested);
  }

  public <S> ConditionGroupBuilder<T> exists(
      String associationPath, Consumer<SubqueryBuilder<S>> body) {
    conditions.add(SubqueryBuilder.buildAssociation(SubqueryKind.EXISTS, associationPath, body));
    return this;
  }

  public <S> ConditionGroupBuilder<T> notExists(
      String associationPath, Consumer<SubqueryBuilder<S>> body) {
    conditions.add(
        SubqueryBuilder.buildAssociation(SubqueryKind.NOT_EXISTS, associationPath, body));
    return this;
  }

  public <S> ConditionGroupBuilder<T> exists(
      Class<S> subEntity, Consumer<SubqueryBuilder<S>> body) {
    conditions.add(SubqueryBuilder.buildEntity(SubqueryKind.EXISTS, subEntity, body));
    return this;
  }

  public <S> ConditionGroupBuilder<T> notExists(
      Class<S> subEntity, Consumer<SubqueryBuilder<S>> body) {
    conditions.add(SubqueryBuilder.buildEntity(SubqueryKind.NOT_EXISTS, subEntity, body));
    return this;
  }

  public <S> ConditionGroupBuilder<T> inSubquery(
      String outerField,
      Class<S> subEntity,
      String subSelectField,
      Consumer<SubqueryBuilder<S>> body) {
    conditions.add(
        SubqueryBuilder.buildIn(SubqueryKind.IN, outerField, subEntity, subSelectField, body));
    return this;
  }

  public <S> ConditionGroupBuilder<T> notInSubquery(
      String outerField,
      Class<S> subEntity,
      String subSelectField,
      Consumer<SubqueryBuilder<S>> body) {
    conditions.add(
        SubqueryBuilder.buildIn(SubqueryKind.NOT_IN, outerField, subEntity, subSelectField, body));
    return this;
  }

  public GroupCondition build() {
    return new GroupCondition(logicalOperator, List.copyOf(conditions));
  }

  private ConditionGroupBuilder<T> group(
      LogicalOperator logic, Consumer<ConditionGroupBuilder<T>> nested) {
    ConditionGroupBuilder<T> builder = new ConditionGroupBuilder<>(logic);
    nested.accept(builder);
    conditions.add(builder.build());
    return this;
  }
}
