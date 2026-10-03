package com.borjaglez.specrepository.core;

import static org.apiguardian.api.API.Status.STABLE;

import java.util.Objects;
import java.util.Set;

import org.apiguardian.api.API;

@API(status = STABLE, since = "1.0.0")
public final class AllowedFieldsPolicy {
  private static final AllowedFieldsPolicy ALLOW_ALL = new AllowedFieldsPolicy(null, null);

  private final Set<String> filterableFields;
  private final Set<String> sortableFields;

  private AllowedFieldsPolicy(Set<String> filterableFields, Set<String> sortableFields) {
    this.filterableFields = filterableFields;
    this.sortableFields = sortableFields;
  }

  public static AllowedFieldsPolicy allowAll() {
    return ALLOW_ALL;
  }

  public static AllowedFieldsPolicy of(Set<String> filterable, Set<String> sortable) {
    Objects.requireNonNull(filterable, "filterable must not be null");
    Objects.requireNonNull(sortable, "sortable must not be null");
    return new AllowedFieldsPolicy(Set.copyOf(filterable), Set.copyOf(sortable));
  }

  public void validateFilter(String field) {
    if (filterableFields != null && !filterableFields.contains(field)) {
      throw new DisallowedFieldException(field, "filtering");
    }
  }

  public void validateSort(String field) {
    if (sortableFields != null && !sortableFields.contains(field)) {
      throw new DisallowedFieldException(field, "sorting");
    }
  }

  /**
   * Checks the client input of {@code plan} against this policy: the fields of its client
   * conditions ({@link QueryPlan#rootCondition()}, including nested groups and the outer fields of
   * subqueries) and its client sort. The server conditions ({@link QueryPlan#serverCondition()})
   * are not checked, and neither are the {@link QueryPlan#having()} conditions (no client channel
   * produces a {@code having}, so it is always server input) nor a sort set on a builder derived
   * from a plan ({@link QueryPlan#toBuilder()}), which is server input as well.
   *
   * @throws DisallowedFieldException for the first field outside the policy
   */
  public void validate(QueryPlan<?> plan) {
    if (isAllowAll()) {
      return;
    }
    validateConditions(plan.rootCondition());
    if (!plan.serverSort()) {
      plan.sort().forEach(order -> validateSort(order.getProperty()));
    }
  }

  public boolean isAllowAll() {
    return this == ALLOW_ALL;
  }

  private void validateConditions(GroupCondition group) {
    for (QueryCondition condition : group.conditions()) {
      if (condition instanceof PredicateCondition predicate) {
        validateFilter(predicate.field());
      } else if (condition instanceof GroupCondition nested) {
        validateConditions(nested);
      } else {
        validateSubqueryOuterFields((SubqueryCondition) condition);
      }
    }
  }

  private void validateSubqueryOuterFields(SubqueryCondition subquery) {
    if (subquery.kind() == SubqueryKind.IN || subquery.kind() == SubqueryKind.NOT_IN) {
      validateFilter(subquery.outerField());
    }
    for (CorrelationPair pair : subquery.correlations()) {
      validateFilter(pair.outerField());
    }
  }
}
