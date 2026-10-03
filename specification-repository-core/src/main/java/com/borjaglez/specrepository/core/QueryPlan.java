package com.borjaglez.specrepository.core;

import static org.apiguardian.api.API.Status.INTERNAL;
import static org.apiguardian.api.API.Status.STABLE;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apiguardian.api.API;
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
 * <p>A plan has no public constructor: it is built with {@link SpecificationQueryBuilder}, {@link
 * QueryPlanBuilder} or the fluent query of a repository. Use {@link #toBuilder()} to derive a plan
 * with server conditions, fetches, sort, grouping or a lock from an existing one. A new setting is
 * added as a new accessor and a new builder method, so it does not break existing code.
 */
@API(status = STABLE, since = "1.0.0")
public final class QueryPlan<T> {

  private final Class<T> entityType;
  private final GroupCondition rootCondition;
  private final GroupCondition serverCondition;
  private final List<JoinInstruction> joins;
  private final List<FetchInstruction> fetches;
  private final List<String> projections;
  private final List<Selection> selections;
  private final Class<?> projectionType;
  private final List<String> groupBy;
  private final List<HavingCondition> having;
  private final Sort sort;
  private final boolean distinct;
  private final AllowedFieldsPolicy allowedFieldsPolicy;
  private final QueryLock lock;

  QueryPlan(
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
    Objects.requireNonNull(serverCondition, "serverCondition must not be null");
    if (serverCondition.logicalOperator() != LogicalOperator.AND) {
      throw new IllegalArgumentException("serverCondition must be an AND group");
    }
    this.entityType = entityType;
    this.rootCondition = rootCondition;
    this.serverCondition = serverCondition;
    this.joins = joins;
    this.fetches = fetches;
    this.projections = projections;
    this.selections = selections;
    this.projectionType = projectionType;
    this.groupBy = groupBy;
    this.having = Objects.requireNonNull(having, "having must not be null");
    this.sort = sort;
    this.distinct = distinct;
    this.allowedFieldsPolicy =
        Objects.requireNonNull(allowedFieldsPolicy, "allowedFieldsPolicy must not be null");
    this.lock = Objects.requireNonNull(lock, "lock must not be null");
  }

  /** The entity the query reads. */
  public Class<T> entityType() {
    return entityType;
  }

  /** The client conditions, checked against the {@link #allowedFieldsPolicy()}. */
  public GroupCondition rootCondition() {
    return rootCondition;
  }

  /** The server conditions: an AND group that is not checked against the policy. */
  public GroupCondition serverCondition() {
    return serverCondition;
  }

  public List<JoinInstruction> joins() {
    return joins;
  }

  public List<FetchInstruction> fetches() {
    return fetches;
  }

  public List<String> projections() {
    return projections;
  }

  public List<Selection> selections() {
    return selections;
  }

  /** The type the selections are read into, or {@code null} when there is none. */
  public Class<?> projectionType() {
    return projectionType;
  }

  public List<String> groupBy() {
    return groupBy;
  }

  public List<HavingCondition> having() {
    return having;
  }

  public Sort sort() {
    return sort;
  }

  public boolean distinct() {
    return distinct;
  }

  public AllowedFieldsPolicy allowedFieldsPolicy() {
    return allowedFieldsPolicy;
  }

  /** The pessimistic row lock of the entity query, {@link QueryLock#NONE} for none. */
  public QueryLock lock() {
    return lock;
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

  /**
   * A copy of this plan without its fetches. The JPA module uses it to read the ids of a page
   * before fetching the entities; it is not meant for application code.
   */
  @API(status = INTERNAL, since = "1.0.0")
  public QueryPlan<T> withoutFetches() {
    return new QueryPlan<>(
        entityType,
        rootCondition,
        serverCondition,
        joins,
        List.of(),
        projections,
        selections,
        projectionType,
        groupBy,
        having,
        sort,
        distinct,
        allowedFieldsPolicy,
        lock);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof QueryPlan<?> that && Arrays.equals(components(), that.components());
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(components());
  }

  @Override
  public String toString() {
    return "QueryPlan[entityType="
        + entityType
        + ", rootCondition="
        + rootCondition
        + ", serverCondition="
        + serverCondition
        + ", joins="
        + joins
        + ", fetches="
        + fetches
        + ", projections="
        + projections
        + ", selections="
        + selections
        + ", projectionType="
        + projectionType
        + ", groupBy="
        + groupBy
        + ", having="
        + having
        + ", sort="
        + sort
        + ", distinct="
        + distinct
        + ", allowedFieldsPolicy="
        + allowedFieldsPolicy
        + ", lock="
        + lock
        + "]";
  }

  private Object[] components() {
    return new Object[] {
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
      lock
    };
  }
}
