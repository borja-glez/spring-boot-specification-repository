package com.borjaglez.specrepository.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OperatorsTest {

  @ParameterizedTest
  @CsvSource({
    "eq",
    "neq",
    "isnull",
    "isnotnull",
    "isempty",
    "isnotempty",
    "contains",
    "notcontains",
    "startswith",
    "endswith",
    "gt",
    "gte",
    "lt",
    "lte",
    "in",
    "notin"
  })
  void allConstantsShouldHaveExpectedValues(String expected) {
    FilterOperator op =
        switch (expected) {
          case "eq" -> Operators.EQUALS;
          case "neq" -> Operators.NOT_EQUALS;
          case "isnull" -> Operators.IS_NULL;
          case "isnotnull" -> Operators.IS_NOT_NULL;
          case "isempty" -> Operators.IS_EMPTY;
          case "isnotempty" -> Operators.IS_NOT_EMPTY;
          case "contains" -> Operators.CONTAINS;
          case "notcontains" -> Operators.NOT_CONTAINS;
          case "startswith" -> Operators.STARTS_WITH;
          case "endswith" -> Operators.ENDS_WITH;
          case "gt" -> Operators.GREATER_THAN;
          case "gte" -> Operators.GREATER_THAN_OR_EQUAL;
          case "lt" -> Operators.LESS_THAN;
          case "lte" -> Operators.LESS_THAN_OR_EQUAL;
          case "in" -> Operators.IN;
          case "notin" -> Operators.NOT_IN;
          default -> throw new IllegalArgumentException("Unknown: " + expected);
        };
    assertThat(op).isNotNull();
    assertThat(op.value()).isEqualTo(expected);
  }

  @Test
  void customShouldCreateOperatorWithGivenValue() {
    FilterOperator op = Operators.custom("between");
    assertThat(op.value()).isEqualTo("between");
  }

  @Test
  void customShouldRejectNull() {
    assertThatNullPointerException()
        .isThrownBy(() -> Operators.custom(null))
        .withMessage("value must not be null");
  }
}
