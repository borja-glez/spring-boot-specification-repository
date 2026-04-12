package com.borjaglez.specrepository.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.springframework.data.domain.Sort;

public class QueryPlanBuilder<T> {
  private final Class<T> entityType;
  private final ConditionGroupBuilder<T> rootGroup =
      new ConditionGroupBuilder<>(LogicalOperator.AND);
  private final List<JoinInstruction> joins = new ArrayList<>();
  private final List<FetchInstruction> fetches = new ArrayList<>();
  private final List<String> projections = new ArrayList<>();
  private final List<Selection> selections = new ArrayList<>();
  private final List<String> groupBy = new ArrayList<>();
  private final List<HavingCondition> having = new ArrayList<>();
  private Sort sort = Sort.unsorted();
  private Class<?> projectionType;
  private boolean distinct;
  private AllowedFieldsPolicy allowedFieldsPolicy = AllowedFieldsPolicy.allowAll();

  public QueryPlanBuilder(Class<T> entityType) {
    this.entityType = Objects.requireNonNull(entityType, "entityType must not be null");
  }

  public QueryPlanBuilder<T> where(String field, FilterOperator operator, Object value) {
    rootGroup.where(field, operator, value);
    return this;
  }

  public QueryPlanBuilder<T> where(
      String field,
      FilterOperator operator,
      Object value,
      boolean ignoreCase,
      boolean includeNulls) {
    rootGroup.where(field, operator, value, ignoreCase, includeNulls);
    return this;
  }

  public QueryPlanBuilder<T> and(Consumer<ConditionGroupBuilder<T>> nested) {
    rootGroup.and(nested);
    return this;
  }

  public QueryPlanBuilder<T> or(Consumer<ConditionGroupBuilder<T>> nested) {
    rootGroup.or(nested);
    return this;
  }

  public <S> QueryPlanBuilder<T> exists(String associationPath, Consumer<SubqueryBuilder<S>> body) {
    rootGroup.exists(associationPath, body);
    return this;
  }

  public <S> QueryPlanBuilder<T> notExists(
      String associationPath, Consumer<SubqueryBuilder<S>> body) {
    rootGroup.notExists(associationPath, body);
    return this;
  }

  public <S> QueryPlanBuilder<T> exists(Class<S> subEntity, Consumer<SubqueryBuilder<S>> body) {
    rootGroup.exists(subEntity, body);
    return this;
  }

  public <S> QueryPlanBuilder<T> notExists(Class<S> subEntity, Consumer<SubqueryBuilder<S>> body) {
    rootGroup.notExists(subEntity, body);
    return this;
  }

  public <S> QueryPlanBuilder<T> inSubquery(
      String outerField,
      Class<S> subEntity,
      String subSelectField,
      Consumer<SubqueryBuilder<S>> body) {
    rootGroup.inSubquery(outerField, subEntity, subSelectField, body);
    return this;
  }

  public <S> QueryPlanBuilder<T> notInSubquery(
      String outerField,
      Class<S> subEntity,
      String subSelectField,
      Consumer<SubqueryBuilder<S>> body) {
    rootGroup.notInSubquery(outerField, subEntity, subSelectField, body);
    return this;
  }

  public QueryPlanBuilder<T> leftJoin(String... paths) {
    return join(JoinMode.LEFT, paths);
  }

  public QueryPlanBuilder<T> innerJoin(String... paths) {
    return join(JoinMode.INNER, paths);
  }

  public QueryPlanBuilder<T> rightJoin(String... paths) {
    return join(JoinMode.RIGHT, paths);
  }

  public QueryPlanBuilder<T> leftFetch(String... paths) {
    return fetch(JoinMode.LEFT, paths);
  }

  public QueryPlanBuilder<T> innerFetch(String... paths) {
    return fetch(JoinMode.INNER, paths);
  }

  public QueryPlanBuilder<T> rightFetch(String... paths) {
    return fetch(JoinMode.RIGHT, paths);
  }

  public QueryPlanBuilder<T> sort(Sort sort) {
    this.sort = Objects.requireNonNull(sort, "sort must not be null");
    return this;
  }

  public QueryPlanBuilder<T> select(String... fields) {
    Arrays.stream(fields).forEach(this::selectField);
    return this;
  }

  public QueryPlanBuilder<T> sum(String field) {
    return aggregate(AggregateFunction.SUM, field, null);
  }

  public QueryPlanBuilder<T> sumAs(String alias, String field) {
    return aggregate(AggregateFunction.SUM, field, alias);
  }

  public QueryPlanBuilder<T> avg(String field) {
    return aggregate(AggregateFunction.AVG, field, null);
  }

  public QueryPlanBuilder<T> avgAs(String alias, String field) {
    return aggregate(AggregateFunction.AVG, field, alias);
  }

  public QueryPlanBuilder<T> min(String field) {
    return aggregate(AggregateFunction.MIN, field, null);
  }

  public QueryPlanBuilder<T> minAs(String alias, String field) {
    return aggregate(AggregateFunction.MIN, field, alias);
  }

  public QueryPlanBuilder<T> max(String field) {
    return aggregate(AggregateFunction.MAX, field, null);
  }

  public QueryPlanBuilder<T> maxAs(String alias, String field) {
    return aggregate(AggregateFunction.MAX, field, alias);
  }

  public QueryPlanBuilder<T> count(String field) {
    return aggregate(AggregateFunction.COUNT, field, null);
  }

  public QueryPlanBuilder<T> countAs(String alias, String field) {
    return aggregate(AggregateFunction.COUNT, field, alias);
  }

  public QueryPlanBuilder<T> aggregate(AggregateFunction function, String field, String alias) {
    selections.add(new AggregateSelection(function, field, alias));
    return this;
  }

  public QueryPlanBuilder<T> groupBy(String... fields) {
    groupBy.addAll(Arrays.asList(fields));
    return this;
  }

  public QueryPlanBuilder<T> having(
      AggregateFunction function, String field, FilterOperator operator, Object value) {
    having.add(new HavingCondition(function, field, operator, value));
    return this;
  }

  public QueryPlanBuilder<T> allowedFields(AllowedFieldsPolicy allowedFieldsPolicy) {
    this.allowedFieldsPolicy =
        Objects.requireNonNull(allowedFieldsPolicy, "allowedFieldsPolicy must not be null");
    return this;
  }

  public QueryPlanBuilder<T> distinct() {
    this.distinct = true;
    return this;
  }

  public <P> ProjectedQueryPlanBuilder<T, P> selectInto(Class<P> projectionType) {
    return new ProjectedQueryPlanBuilder<>(selectIntoInternal(projectionType));
  }

  public QueryPlan<T> build() {
    if (!having.isEmpty() && groupBy.isEmpty()) {
      throw new IllegalStateException("having requires at least one groupBy field");
    }
    return new QueryPlan<>(
        entityType,
        rootGroup.build(),
        List.copyOf(joins),
        List.copyOf(fetches),
        List.copyOf(projections),
        List.copyOf(selections),
        projectionType,
        List.copyOf(groupBy),
        List.copyOf(having),
        sort,
        distinct,
        allowedFieldsPolicy);
  }

  protected final <P> QueryPlanBuilder<T> selectIntoInternal(Class<P> projectionType) {
    Objects.requireNonNull(projectionType, "projectionType must not be null");
    if (!hasSelections()) {
      throw new IllegalStateException(
          "select and/or aggregate selection methods must be called before selectInto");
    }
    this.projectionType = projectionType;
    return this;
  }

  protected final boolean hasSelections() {
    return !selections.isEmpty();
  }

  private void selectField(String field) {
    projections.add(field);
    selections.add(new FieldSelection(field));
  }

  private QueryPlanBuilder<T> join(JoinMode mode, String... paths) {
    Arrays.stream(paths).forEach(path -> joins.add(new JoinInstruction(path, mode)));
    return this;
  }

  private QueryPlanBuilder<T> fetch(JoinMode mode, String... paths) {
    Arrays.stream(paths).forEach(path -> fetches.add(new FetchInstruction(path, mode)));
    return this;
  }
}
