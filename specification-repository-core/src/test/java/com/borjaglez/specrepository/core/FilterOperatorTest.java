package com.borjaglez.specrepository.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class FilterOperatorTest {

  @Test
  void shouldCreateWithValidValue() {
    FilterOperator op = new FilterOperator("eq");
    assertThat(op.value()).isEqualTo("eq");
  }

  @Test
  void shouldRejectNullValue() {
    assertThatNullPointerException()
        .isThrownBy(() -> new FilterOperator(null))
        .withMessage("value must not be null");
  }

  @Test
  void shouldRejectBlankValue() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new FilterOperator(""))
        .withMessage("value must not be blank");
  }

  @Test
  void shouldRejectWhitespaceOnlyValue() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new FilterOperator("   "))
        .withMessage("value must not be blank");
  }

  @Test
  void ofFactoryShouldCreateOperator() {
    FilterOperator op = FilterOperator.of("custom");
    assertThat(op.value()).isEqualTo("custom");
  }
}
