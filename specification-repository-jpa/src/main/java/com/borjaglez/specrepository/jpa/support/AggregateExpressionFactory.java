package com.borjaglez.specrepository.jpa.support;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;

import com.borjaglez.specrepository.core.AggregateFunction;

public final class AggregateExpressionFactory {
  private AggregateExpressionFactory() {}

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Expression<?> create(
      CriteriaBuilder builder, AggregateFunction function, String field, Path<?> path) {
    return switch (function) {
      case SUM -> builder.sum(asNumberExpression(function, field, path));
      case AVG -> builder.avg(asNumberExpression(function, field, path));
      case MIN -> {
        if (Number.class.isAssignableFrom(path.getJavaType())) {
          yield builder.min((Expression<? extends Number>) path);
        }
        if (Comparable.class.isAssignableFrom(path.getJavaType())) {
          yield builder.least((Expression<? extends Comparable>) path);
        }
        throw new IllegalArgumentException(
            "MIN requires a comparable field: " + path.getJavaType());
      }
      case MAX -> {
        if (Number.class.isAssignableFrom(path.getJavaType())) {
          yield builder.max((Expression<? extends Number>) path);
        }
        if (Comparable.class.isAssignableFrom(path.getJavaType())) {
          yield builder.greatest((Expression<? extends Comparable>) path);
        }
        throw new IllegalArgumentException(
            "MAX requires a comparable field: " + path.getJavaType());
      }
      case COUNT -> builder.count(path);
    };
  }

  @SuppressWarnings("unchecked")
  private static Expression<? extends Number> asNumberExpression(
      AggregateFunction function, String field, Path<?> path) {
    if (!Number.class.isAssignableFrom(path.getJavaType())) {
      throw new IllegalArgumentException(function + " requires a numeric field: " + field);
    }
    return (Expression<? extends Number>) path;
  }
}
