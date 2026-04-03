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
  private final List<String> groupBy = new ArrayList<>();
  private Sort sort = Sort.unsorted();
  private boolean distinct;

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
    projections.addAll(Arrays.asList(fields));
    return this;
  }

  public QueryPlanBuilder<T> groupBy(String... fields) {
    groupBy.addAll(Arrays.asList(fields));
    return this;
  }

  public QueryPlanBuilder<T> distinct() {
    this.distinct = true;
    return this;
  }

  public QueryPlan<T> build() {
    return new QueryPlan<>(
        entityType,
        rootGroup.build(),
        List.copyOf(joins),
        List.copyOf(fetches),
        List.copyOf(projections),
        List.copyOf(groupBy),
        sort,
        distinct);
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
