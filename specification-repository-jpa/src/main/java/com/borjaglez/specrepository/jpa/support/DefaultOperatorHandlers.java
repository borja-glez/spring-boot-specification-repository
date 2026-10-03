package com.borjaglez.specrepository.jpa.support;

import static org.apiguardian.api.API.Status.INTERNAL;

import java.util.Collection;
import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import org.apiguardian.api.API;

import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.jpa.spi.OperatorContext;
import com.borjaglez.specrepository.jpa.spi.OperatorHandler;

@API(status = INTERNAL, since = "1.0.0")
public final class DefaultOperatorHandlers {
  private static final String UNACCENT = "unaccent";
  private static final char LIKE_ESCAPE = '\\';

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
            Operators.CONTAINS,
            context -> stringLike(context, "%" + escapeLike(context.value()) + "%", false)),
        handler(
            Operators.NOT_CONTAINS,
            context -> stringLike(context, "%" + escapeLike(context.value()) + "%", true)),
        handler(
            Operators.STARTS_WITH,
            context -> stringLike(context, escapeLike(context.value()) + "%", false)),
        handler(
            Operators.ENDS_WITH,
            context -> stringLike(context, "%" + escapeLike(context.value()), false)),
        handler(Operators.GREATER_THAN, context -> comparable(context, ComparisonMode.GT)),
        handler(
            Operators.GREATER_THAN_OR_EQUAL, context -> comparable(context, ComparisonMode.GTE)),
        handler(Operators.LESS_THAN, context -> comparable(context, ComparisonMode.LT)),
        handler(Operators.LESS_THAN_OR_EQUAL, context -> comparable(context, ComparisonMode.LTE)),
        handler(Operators.BETWEEN, DefaultOperatorHandlers::between),
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
    if (context.value() == null) {
      // "= NULL" is never true in SQL: eq/neq with null mean is null / is not null.
      return negate ? cb.isNotNull(context.path()) : cb.isNull(context.path());
    }
    if (context.ignoreCase()) {
      Expression<String> column = normalized(cb, context.path());
      Expression<String> value = normalizedValue(cb, context.value().toString());
      return negate ? cb.notEqual(column, value) : cb.equal(column, value);
    }
    Object value = context.value();
    return negate ? cb.notEqual(context.path(), value) : cb.equal(context.path(), value);
  }

  private static Predicate stringLike(OperatorContext context, String pattern, boolean negate) {
    CriteriaBuilder cb = context.criteriaBuilder();
    Predicate predicate =
        context.ignoreCase()
            ? cb.like(normalized(cb, context.path()), normalizedValue(cb, pattern), LIKE_ESCAPE)
            : cb.like(context.path().as(String.class), pattern, LIKE_ESCAPE);
    return negate ? predicate.not() : predicate;
  }

  /**
   * Makes the search term a literal inside a {@code LIKE} pattern: the escape character, {@code %}
   * and {@code _} are prefixed with {@link #LIKE_ESCAPE}. The escape character goes first so the
   * escapes added for {@code %} and {@code _} are not escaped again. {@code upper} and {@code
   * unaccent} leave these characters unchanged, so the escaped term can be normalized afterwards.
   */
  private static String escapeLike(Object value) {
    return String.valueOf(value).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Predicate between(OperatorContext context) {
    RangeValues range = rangeValues(context.value());
    Path path = context.path();
    return context.criteriaBuilder().between(path, range.lower(), range.upper());
  }

  private static RangeValues rangeValues(Object value) {
    if (!(value instanceof Iterable<?> iterable)) {
      throw new IllegalArgumentException("BETWEEN operator requires exactly 2 values");
    }
    List<?> values =
        iterable instanceof List<?> list
            ? list
            : java.util.stream.StreamSupport.stream(iterable.spliterator(), false).toList();
    if (values.size() != 2) {
      throw new IllegalArgumentException("BETWEEN operator requires exactly 2 values");
    }
    return new RangeValues(comparable(values.get(0)), comparable(values.get(1)));
  }

  @SuppressWarnings("rawtypes")
  private static Comparable comparable(Object value) {
    if (value == null) {
      throw new IllegalArgumentException("BETWEEN operator values must not be null");
    }
    if (!(value instanceof Comparable comparable)) {
      throw new IllegalArgumentException("BETWEEN operator values must implement Comparable");
    }
    return comparable;
  }

  private static Expression<String> normalized(CriteriaBuilder criteriaBuilder, Path<?> path) {
    return criteriaBuilder.function(
        UNACCENT, String.class, criteriaBuilder.upper(path.as(String.class)));
  }

  /**
   * Normalizes the search term exactly like the column: both sides go through the database's {@code
   * unaccent(upper(...))}, so "café" matches "CAFE" and the result does not depend on the JVM
   * default locale.
   *
   * <p>The term is bound as a query parameter, never inlined in the SQL. {@link
   * CriteriaBuilder#literal} would inline it, and {@link CriteriaBuilder#parameter} would need a
   * value set on the query, which a {@code Specification} cannot do. A plain value passed to {@link
   * CriteriaBuilder#concat(String, Expression)} is bound like the pattern of the case-sensitive
   * {@code like}, so the term is concatenated with an empty string literal, which renders as {@code
   * unaccent(upper((?||'')))}.
   */
  private static Expression<String> normalizedValue(CriteriaBuilder criteriaBuilder, String value) {
    Expression<String> term = criteriaBuilder.concat(value, criteriaBuilder.literal(""));
    return criteriaBuilder.function(UNACCENT, String.class, criteriaBuilder.upper(term));
  }

  private enum ComparisonMode {
    GT,
    GTE,
    LT,
    LTE
  }

  @SuppressWarnings("rawtypes")
  private record RangeValues(Comparable lower, Comparable upper) {}
}
