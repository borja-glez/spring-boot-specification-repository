package com.borjaglez.specrepository.jpa.support;

import java.util.Collection;
import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.jpa.spi.OperatorContext;
import com.borjaglez.specrepository.jpa.spi.OperatorHandler;

public final class DefaultOperatorHandlers {
  private static final String UNACCENT = "unaccent";

  private DefaultOperatorHandlers() {}

  @SuppressWarnings("unchecked")
  public static Collection<OperatorHandler> defaults() {
    return List.of(
        handler(Operators.EQUALS, context -> compare(context, false)),
        handler(Operators.NOT_EQUALS, context -> compare(context, true)),
        handler(Operators.IS_NULL, context -> context.criteriaBuilder().isNull(context.path())),
        handler(
            Operators.IS_NOT_NULL, context -> context.criteriaBuilder().isNotNull(context.path())),
        handler(
            Operators.IS_EMPTY,
            context ->
                context
                    .criteriaBuilder()
                    .isEmpty((Expression<java.util.Collection<?>>) context.path())),
        handler(
            Operators.IS_NOT_EMPTY,
            context ->
                context
                    .criteriaBuilder()
                    .isNotEmpty((Expression<java.util.Collection<?>>) context.path())),
        handler(
            Operators.CONTAINS, context -> stringLike(context, "%" + context.value() + "%", false)),
        handler(
            Operators.NOT_CONTAINS,
            context -> stringLike(context, "%" + context.value() + "%", true)),
        handler(
            Operators.STARTS_WITH, context -> stringLike(context, context.value() + "%", false)),
        handler(Operators.ENDS_WITH, context -> stringLike(context, "%" + context.value(), false)),
        handler(Operators.GREATER_THAN, context -> comparable(context, ComparisonMode.GT)),
        handler(
            Operators.GREATER_THAN_OR_EQUAL, context -> comparable(context, ComparisonMode.GTE)),
        handler(Operators.LESS_THAN, context -> comparable(context, ComparisonMode.LT)),
        handler(Operators.LESS_THAN_OR_EQUAL, context -> comparable(context, ComparisonMode.LTE)),
        handler(Operators.IN, context -> in(context, false)),
        handler(Operators.NOT_IN, context -> in(context, true)));
  }

  private static OperatorHandler handler(
      FilterOperator operator, java.util.function.Function<OperatorContext, Predicate> function) {
    return new OperatorHandler() {
      @Override
      public FilterOperator operator() {
        return operator;
      }

      @Override
      public Predicate create(OperatorContext context) {
        return function.apply(context);
      }
    };
  }

  private static Predicate compare(OperatorContext context, boolean negate) {
    CriteriaBuilder cb = context.criteriaBuilder();
    Expression<?> expression =
        context.ignoreCase() ? normalized(cb, context.path()) : context.path();
    Object value =
        context.ignoreCase() && context.value() != null
            ? context.value().toString().toUpperCase()
            : context.value();
    Predicate predicate = negate ? cb.notEqual(expression, value) : cb.equal(expression, value);
    return predicate;
  }

  private static Predicate stringLike(OperatorContext context, String pattern, boolean negate) {
    CriteriaBuilder cb = context.criteriaBuilder();
    Expression<String> expression =
        context.ignoreCase() ? normalized(cb, context.path()) : context.path().as(String.class);
    String value = context.ignoreCase() ? pattern.toUpperCase() : pattern;
    Predicate predicate = cb.like(expression, value);
    return negate ? predicate.not() : predicate;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Predicate comparable(OperatorContext context, ComparisonMode mode) {
    CriteriaBuilder cb = context.criteriaBuilder();
    Path path = context.path();
    Comparable value = (Comparable) context.value();
    return switch (mode) {
      case GT -> cb.greaterThan(path, value);
      case GTE -> cb.greaterThanOrEqualTo(path, value);
      case LT -> cb.lessThan(path, value);
      case LTE -> cb.lessThanOrEqualTo(path, value);
    };
  }

  private static Predicate in(OperatorContext context, boolean negate) {
    CriteriaBuilder.In<Object> predicate = context.criteriaBuilder().in(context.path());
    Object value = context.value();
    if (value instanceof Iterable<?> iterable) {
      iterable.forEach(predicate::value);
    } else {
      predicate.value(value);
    }
    return negate ? predicate.not() : predicate;
  }

  private static Expression<String> normalized(CriteriaBuilder criteriaBuilder, Path<?> path) {
    return criteriaBuilder.function(
        UNACCENT, String.class, criteriaBuilder.upper(path.as(String.class)));
  }

  private enum ComparisonMode {
    GT,
    GTE,
    LT,
    LTE
  }
}
