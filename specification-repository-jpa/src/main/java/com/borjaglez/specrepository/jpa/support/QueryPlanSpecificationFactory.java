package com.borjaglez.specrepository.jpa.support;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.ManagedType;

import org.springframework.data.jpa.domain.Specification;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.CorrelationMode;
import com.borjaglez.specrepository.core.CorrelationPair;
import com.borjaglez.specrepository.core.FetchInstruction;
import com.borjaglez.specrepository.core.GroupCondition;
import com.borjaglez.specrepository.core.JoinInstruction;
import com.borjaglez.specrepository.core.JoinMode;
import com.borjaglez.specrepository.core.LogicalOperator;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.core.QueryCondition;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.core.SubqueryCondition;
import com.borjaglez.specrepository.core.SubqueryKind;
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

      Predicate predicate =
          toPredicate(
              plan.rootCondition(), root, root.getModel(), query, criteriaBuilder, registry);
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
      From<?, ?> from,
      ManagedType<?> fromType,
      CriteriaQuery<?> query,
      CriteriaBuilder criteriaBuilder,
      AssociationRegistry registry) {
    List<Predicate> predicates = new ArrayList<>();
    for (QueryCondition queryCondition : condition.conditions()) {
      if (queryCondition instanceof GroupCondition groupCondition) {
        Predicate nested =
            toPredicate(groupCondition, from, fromType, query, criteriaBuilder, registry);
        if (nested != null) {
          predicates.add(nested);
        }
        continue;
      }
      if (queryCondition instanceof SubqueryCondition subqueryCondition) {
        predicates.add(
            translateSubquery(subqueryCondition, from, fromType, query, criteriaBuilder, registry));
        continue;
      }
      PredicateCondition predicateCondition = (PredicateCondition) queryCondition;
      Path<?> path = resolvePath(from, fromType, registry, predicateCondition.field());
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

  private Predicate translateSubquery(
      SubqueryCondition sc,
      From<?, ?> outer,
      ManagedType<?> outerType,
      CriteriaQuery<?> query,
      CriteriaBuilder cb,
      AssociationRegistry outerRegistry) {
    Subquery<Integer> sub = query.subquery(Integer.class);
    AssociationRegistry subRegistry = new AssociationRegistry();
    From<?, ?> subRoot;
    ManagedType<?> subRootType;
    Predicate correlationPredicate = null;

    if (sc.correlationMode() == CorrelationMode.ASSOCIATION) {
      From<?, ?> correlatedOuter = correlateOuter(sub, outer);
      String[] segments = sc.associationPath().split("\\.");
      From<?, ?> navigated = correlatedOuter;
      ManagedType<?> currentType = outerType;
      for (String segment : segments) {
        Join<?, ?> join = navigated.join(segment, AssociationRegistry.toJoinType(JoinMode.INNER));
        navigated = join;
        currentType = pathResolver.resolveAssociationTarget(currentType, segment);
      }
      subRoot = navigated;
      subRootType = currentType;
    } else {
      @SuppressWarnings("unchecked")
      Class<Object> entityClass = (Class<Object>) sc.subEntity();
      Root<Object> from = sub.from(entityClass);
      subRoot = from;
      subRootType = from.getModel();
      List<Predicate> correlationPredicates = new ArrayList<>();
      for (CorrelationPair pair : sc.correlations()) {
        Path<?> outerPath = resolvePath(outer, outerType, outerRegistry, pair.outerField());
        Path<?> innerPath = resolvePath(subRoot, subRootType, subRegistry, pair.innerField());
        correlationPredicates.add(cb.equal(innerPath, outerPath));
      }
      if (!correlationPredicates.isEmpty()) {
        correlationPredicate = cb.and(correlationPredicates.toArray(Predicate[]::new));
      }
    }

    Predicate body = toPredicate(sc.subCondition(), subRoot, subRootType, query, cb, subRegistry);

    List<Predicate> whereParts = new ArrayList<>();
    if (correlationPredicate != null) {
      whereParts.add(correlationPredicate);
    }
    if (body != null) {
      whereParts.add(body);
    }
    if (!whereParts.isEmpty()) {
      sub.where(whereParts.toArray(Predicate[]::new));
    }

    return switch (sc.kind()) {
      case EXISTS -> {
        sub.select(cb.literal(1));
        yield cb.exists(sub);
      }
      case NOT_EXISTS -> {
        sub.select(cb.literal(1));
        yield cb.not(cb.exists(sub));
      }
      case IN -> {
        @SuppressWarnings({"rawtypes", "unchecked"})
        Subquery<Object> projected = (Subquery) sub;
        Path<?> innerSelect = resolvePath(subRoot, subRootType, subRegistry, sc.subSelectField());
        @SuppressWarnings({"rawtypes", "unchecked"})
        Expression<Object> innerExpression = (Expression) innerSelect;
        projected.select(innerExpression);
        Path<?> outerPath = resolvePath(outer, outerType, outerRegistry, sc.outerField());
        yield outerPath.in(projected);
      }
      case NOT_IN -> {
        @SuppressWarnings({"rawtypes", "unchecked"})
        Subquery<Object> projected = (Subquery) sub;
        Path<?> innerSelect = resolvePath(subRoot, subRootType, subRegistry, sc.subSelectField());
        @SuppressWarnings({"rawtypes", "unchecked"})
        Expression<Object> innerExpression = (Expression) innerSelect;
        projected.select(innerExpression);
        Path<?> outerPath = resolvePath(outer, outerType, outerRegistry, sc.outerField());
        yield cb.not(outerPath.in(projected));
      }
    };
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
      if (condition instanceof SubqueryCondition subquery) {
        validateSubqueryOuterFields(policy, subquery);
      }
    }
  }

  private Path<?> resolvePath(
      From<?, ?> from, ManagedType<?> fromType, AssociationRegistry registry, String field) {
    if (from instanceof Root<?> rootFrom) {
      return pathResolver.resolve(rootFrom, registry, field, JoinMode.LEFT);
    }
    return pathResolver.resolve(from, fromType, registry, field, JoinMode.LEFT);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private From<?, ?> correlateOuter(Subquery<?> sub, From<?, ?> outer) {
    if (outer instanceof Root<?> outerRoot) {
      return sub.correlate((Root) outerRoot);
    }
    return sub.correlate((Join) outer);
  }

  private void validateSubqueryOuterFields(AllowedFieldsPolicy policy, SubqueryCondition subquery) {
    if (subquery.kind() == SubqueryKind.IN || subquery.kind() == SubqueryKind.NOT_IN) {
      policy.validateFilter(subquery.outerField());
    }
    for (CorrelationPair pair : subquery.correlations()) {
      policy.validateFilter(pair.outerField());
    }
  }
}
