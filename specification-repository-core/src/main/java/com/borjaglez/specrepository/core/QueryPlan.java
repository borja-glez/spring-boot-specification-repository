package com.borjaglez.specrepository.core;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Sort;

/**
 * An immutable description of a query over {@code T}.
 *
 * <p>The conditions come in two parts, combined with AND when the query runs:
 *
 * <ul>
 *   <li>{@code rootCondition} holds the client conditions, such as the filters parsed from an HTTP
 *       request. They are checked against the {@link AllowedFieldsPolicy}.
 *   <li>{@code serverCondition} holds the conditions the server adds, such as "only the orders of
 *       the current customer". It is an AND group that is not checked against the policy, so it can
 *       use fields the client may not filter by. The client conditions cannot widen it: an OR in
 *       the client part stays inside the client part.
 * </ul>
 *
 * <p>{@code lock} is the pessimistic row lock the entity query takes, {@link QueryLock#NONE} by
 * default. It is set only in code, through {@link QueryPlanBuilder#lock(LockMode, LockWait)}: a
 * plan parsed from a request never carries one.
 *
 * <p>Use {@link #toBuilder()} to derive a plan with server conditions, fetches, sort, grouping or a
 * lock from an existing one.
 */
public record QueryPlan<T>(
    Class<T> entityType,
    GroupCondition rootCondition,
    GroupCondition serverCondition,
    List<JoinInstruction> joins,
    List<FetchInstruction> fetches,
    List<String> projections,
    List<Selection> selections,
    Class<?> projectionType,
    List<String> groupBy,
    List<HavingCondition> having,
    Sort sort,
    boolean distinct,
    AllowedFieldsPolicy allowedFieldsPolicy,
    QueryLock lock) {

  private static final GroupCondition NO_SERVER_CONDITION =
      new GroupCondition(LogicalOperator.AND, List.of());

  public QueryPlan {
    Objects.requireNonNull(serverCondition, "serverCondition must not be null");
    if (serverCondition.logicalOperator() != LogicalOperator.AND) {
      throw new IllegalArgumentException("serverCondition must be an AND group");
    }
    Objects.requireNonNull(having, "having must not be null");
    Objects.requireNonNull(allowedFieldsPolicy, "allowedFieldsPolicy must not be null");
    Objects.requireNonNull(lock, "lock must not be null");
  }

  /** A plan without a lock. */
  public QueryPlan(
      Class<T> entityType,
      GroupCondition rootCondition,
      GroupCondition serverCondition,
      List<JoinInstruction> joins,
      List<FetchInstruction> fetches,
      List<String> projections,
      List<Selection> selections,
      Class<?> projectionType,
      List<String> groupBy,
      List<HavingCondition> having,
      Sort sort,
      boolean distinct,
      AllowedFieldsPolicy allowedFieldsPolicy) {
    this(
        entityType,
        rootCondition,
        serverCondition,
        joins,
        fetches,
        projections,
        selections,
        projectionType,
        groupBy,
        having,
        sort,
        distinct,
        allowedFieldsPolicy,
        QueryLock.NONE);
  }

  /** A plan without server conditions or lock: every condition is a client condition. */
  public QueryPlan(
      Class<T> entityType,
      GroupCondition rootCondition,
      List<JoinInstruction> joins,
      List<FetchInstruction> fetches,
      List<String> projections,
      List<Selection> selections,
      Class<?> projectionType,
      List<String> groupBy,
      List<HavingCondition> having,
      Sort sort,
      boolean distinct,
      AllowedFieldsPolicy allowedFieldsPolicy) {
    this(
        entityType,
        rootCondition,
        NO_SERVER_CONDITION,
        joins,
        fetches,
        projections,
        selections,
        projectionType,
        groupBy,
        having,
        sort,
        distinct,
        allowedFieldsPolicy);
  }

  public QueryPlan(
      Class<T> entityType,
      GroupCondition rootCondition,
      List<JoinInstruction> joins,
      List<FetchInstruction> fetches,
      List<String> projections,
      List<Selection> selections,
      Class<?> projectionType,
      List<String> groupBy,
      Sort sort,
      boolean distinct) {
    this(
        entityType,
        rootCondition,
        joins,
        fetches,
        projections,
        selections,
        projectionType,
        groupBy,
        List.of(),
        sort,
        distinct,
        AllowedFieldsPolicy.allowAll());
  }

  public QueryPlan(
      Class<T> entityType,
      GroupCondition rootCondition,
      List<JoinInstruction> joins,
      List<FetchInstruction> fetches,
      List<String> projections,
      List<Selection> selections,
      Class<?> projectionType,
      List<String> groupBy,
      Sort sort,
      boolean distinct,
      AllowedFieldsPolicy allowedFieldsPolicy) {
    this(
        entityType,
        rootCondition,
        joins,
        fetches,
        projections,
        selections,
        projectionType,
        groupBy,
        List.of(),
        sort,
        distinct,
        allowedFieldsPolicy);
  }

  /**
   * A builder seeded with every component of this plan. Conditions added through it are server
   * conditions: they go to {@link #serverCondition()}, ANDed with the client conditions, and are
   * not checked against the policy. The policy, the client conditions and the other components are
   * kept unless changed; {@link QueryPlanBuilder#sort(Sort)} replaces the sort and {@link
   * QueryPlanBuilder#sortedByDefault(Sort)} sets it only when the plan has none. This plan is not
   * changed.
   */
  public QueryPlanBuilder<T> toBuilder() {
    return new QueryPlanBuilder<>(this);
  }

  public boolean hasSelections() {
    return !selections.isEmpty();
  }

  public boolean hasAggregates() {
    return selections.stream().anyMatch(AggregateSelection.class::isInstance);
  }
}
