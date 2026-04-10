package com.borjaglez.specrepository.jpa.support;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.FetchInstruction;
import com.borjaglez.specrepository.core.GroupCondition;
import com.borjaglez.specrepository.core.JoinInstruction;
import com.borjaglez.specrepository.core.JoinMode;
import com.borjaglez.specrepository.core.LogicalOperator;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.core.QueryCondition;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.jpa.spi.OperatorContext;

public class QueryPlanSpecificationFactory {
  private final OperatorRegistry operatorRegistry;
  private final ValueConversionService valueConversionService;
  private final PathResolver pathResolver;

  public QueryPlanSpecificationFactory(
      OperatorRegistry operatorRegistry,
      ValueConversionService valueConversionService,
      PathResolver pathResolver) {
    this.operatorRegistry = operatorRegistry;
    this.valueConversionService = valueConversionService;
    this.pathResolver = pathResolver;
  }

  public OperatorRegistry operatorRegistry() {
    return operatorRegistry;
  }

  public ValueConversionService valueConversionService() {
    return valueConversionService;
  }

  public PathResolver pathResolver() {
    return pathResolver;
  }

  public <T> Specification<T> create(QueryPlan<T> plan) {
    validateFields(plan);
    return (root, query, criteriaBuilder) -> {
      AssociationRegistry registry = new AssociationRegistry();
      applyJoins(root, registry, plan.joins());
      applyFetches(root, query, registry, plan.fetches());
      applyGrouping(root, query, registry, plan.groupBy());

      if (plan.distinct()) {
        query.distinct(true);
      }

      Predicate predicate = toPredicate(plan.rootCondition(), root, criteriaBuilder, registry);
      if (predicate != null) {
        query.where(predicate);
      }
      return predicate;
    };
  }

  private void applyJoins(
      Root<?> root, AssociationRegistry registry, List<JoinInstruction> instructions) {
    instructions.forEach(
        instruction -> pathResolver.join(root, registry, instruction.path(), instruction.mode()));
  }

  private void applyFetches(
      Root<?> root,
      CriteriaQuery<?> query,
      AssociationRegistry registry,
      List<FetchInstruction> instructions) {
    if (Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType())) {
      return;
    }
    instructions.forEach(
        instruction -> pathResolver.fetch(root, registry, instruction.path(), instruction.mode()));
  }

  private void applyGrouping(
      Root<?> root, CriteriaQuery<?> query, AssociationRegistry registry, List<String> fields) {
    if (fields.isEmpty()) {
      return;
    }
    List<Expression<?>> expressions = new ArrayList<>();
    fields.forEach(
        field -> expressions.add(pathResolver.resolve(root, registry, field, JoinMode.LEFT)));
    query.groupBy(expressions);
  }

  private Predicate toPredicate(
      GroupCondition condition,
      Root<?> root,
      CriteriaBuilder criteriaBuilder,
      AssociationRegistry registry) {
    List<Predicate> predicates = new ArrayList<>();
    for (QueryCondition queryCondition : condition.conditions()) {
      if (queryCondition instanceof GroupCondition groupCondition) {
        Predicate nested = toPredicate(groupCondition, root, criteriaBuilder, registry);
        if (nested != null) {
          predicates.add(nested);
        }
        continue;
      }
      PredicateCondition predicateCondition = (PredicateCondition) queryCondition;
      Path<?> path =
          pathResolver.resolve(root, registry, predicateCondition.field(), JoinMode.LEFT);
      Object convertedValue =
          valueConversionService.convert(
              predicateCondition.value(), path.getJavaType(), predicateCondition.operator());
      Predicate predicate =
          operatorRegistry
              .get(predicateCondition.operator())
              .create(
                  new OperatorContext(
                      criteriaBuilder, path, convertedValue, predicateCondition.ignoreCase()));
      predicates.add(
          predicateCondition.includeNulls()
              ? criteriaBuilder.or(predicate, criteriaBuilder.isNull(path))
              : predicate);
    }

    if (predicates.isEmpty()) {
      return null;
    }

    Predicate[] predicateArray = predicates.toArray(Predicate[]::new);
    return condition.logicalOperator() == LogicalOperator.OR
        ? criteriaBuilder.or(predicateArray)
        : criteriaBuilder.and(predicateArray);
  }

  private <T> void validateFields(QueryPlan<T> plan) {
    AllowedFieldsPolicy policy = plan.allowedFieldsPolicy();
    if (policy.isAllowAll()) {
      return;
    }
    validateFilterFields(policy, plan.rootCondition());
    if (plan.sort().isSorted()) {
      plan.sort().forEach(order -> policy.validateSort(order.getProperty()));
    }
  }

  private void validateFilterFields(AllowedFieldsPolicy policy, GroupCondition group) {
    for (QueryCondition condition : group.conditions()) {
      if (condition instanceof PredicateCondition predicate) {
        policy.validateFilter(predicate.field());
      }
      if (condition instanceof GroupCondition nested) {
        validateFilterFields(policy, nested);
      }
    }
  }
}
