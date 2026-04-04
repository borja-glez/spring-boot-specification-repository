package com.borjaglez.specrepository.core;

import java.util.Objects;

public final class Operators {
  public static final FilterOperator EQUALS = FilterOperator.of("eq");
  public static final FilterOperator NOT_EQUALS = FilterOperator.of("neq");
  public static final FilterOperator IS_NULL = FilterOperator.of("isnull");
  public static final FilterOperator IS_NOT_NULL = FilterOperator.of("isnotnull");
  public static final FilterOperator IS_EMPTY = FilterOperator.of("isempty");
  public static final FilterOperator IS_NOT_EMPTY = FilterOperator.of("isnotempty");
  public static final FilterOperator CONTAINS = FilterOperator.of("contains");
  public static final FilterOperator NOT_CONTAINS = FilterOperator.of("notcontains");
  public static final FilterOperator STARTS_WITH = FilterOperator.of("startswith");
  public static final FilterOperator ENDS_WITH = FilterOperator.of("endswith");
  public static final FilterOperator GREATER_THAN = FilterOperator.of("gt");
  public static final FilterOperator GREATER_THAN_OR_EQUAL = FilterOperator.of("gte");
  public static final FilterOperator LESS_THAN = FilterOperator.of("lt");
  public static final FilterOperator LESS_THAN_OR_EQUAL = FilterOperator.of("lte");
  public static final FilterOperator BETWEEN = FilterOperator.of("between");
  public static final FilterOperator IN = FilterOperator.of("in");
  public static final FilterOperator NOT_IN = FilterOperator.of("notin");

  private Operators() {}

  public static FilterOperator custom(String value) {
    return FilterOperator.of(Objects.requireNonNull(value, "value must not be null"));
  }
}
