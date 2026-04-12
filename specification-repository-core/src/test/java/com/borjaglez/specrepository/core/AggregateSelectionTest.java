package com.borjaglez.specrepository.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class AggregateSelectionTest {

  @Test
  void shouldDefaultAliasToNullWhenLegacyConstructorUsed() {
    AggregateSelection selection = new AggregateSelection(AggregateFunction.SUM, "amount");
    assertThat(selection.alias()).isNull();
    assertThat(selection.columnName()).isEqualTo("SUM_amount");
  }

  @Test
  void shouldUseAliasAsColumnNameWhenProvided() {
    AggregateSelection selection =
        new AggregateSelection(AggregateFunction.AVG, "score", "averageScore");
    assertThat(selection.alias()).isEqualTo("averageScore");
    assertThat(selection.columnName()).isEqualTo("averageScore");
  }

  @Test
  void shouldRejectNullFunction() {
    assertThatNullPointerException()
        .isThrownBy(() -> new AggregateSelection(null, "field"))
        .withMessage("function must not be null");
  }

  @Test
  void shouldRejectNullField() {
    assertThatNullPointerException()
        .isThrownBy(() -> new AggregateSelection(AggregateFunction.SUM, null))
        .withMessage("field must not be null");
  }

  @Test
  void shouldRejectBlankAlias() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AggregateSelection(AggregateFunction.SUM, "amount", "  "))
        .withMessage("alias must not be blank");
  }
}
