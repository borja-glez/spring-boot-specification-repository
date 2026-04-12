package com.borjaglez.specrepository.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class HavingConditionTest {

  @Test
  void shouldExposeAllComponents() {
    HavingCondition condition =
        new HavingCondition(AggregateFunction.SUM, "amount", Operators.GREATER_THAN, 100);
    assertThat(condition.function()).isEqualTo(AggregateFunction.SUM);
    assertThat(condition.field()).isEqualTo("amount");
    assertThat(condition.operator()).isEqualTo(Operators.GREATER_THAN);
    assertThat(condition.value()).isEqualTo(100);
  }

  @Test
  void shouldRejectNullFunction() {
    assertThatNullPointerException()
        .isThrownBy(() -> new HavingCondition(null, "amount", Operators.EQUALS, 1))
        .withMessage("function must not be null");
  }

  @Test
  void shouldRejectNullField() {
    assertThatNullPointerException()
        .isThrownBy(() -> new HavingCondition(AggregateFunction.SUM, null, Operators.EQUALS, 1))
        .withMessage("field must not be null");
  }

  @Test
  void shouldRejectNullOperator() {
    assertThatNullPointerException()
        .isThrownBy(() -> new HavingCondition(AggregateFunction.SUM, "amount", null, 1))
        .withMessage("operator must not be null");
  }

  @Test
  void shouldAllowNullValueForUnaryOperators() {
    HavingCondition condition =
        new HavingCondition(AggregateFunction.SUM, "amount", Operators.IS_NULL, null);
    assertThat(condition.value()).isNull();
  }
}
