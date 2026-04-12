package com.borjaglez.specrepository.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class CorrelationPairTest {

  @Test
  void shouldExposeFields() {
    CorrelationPair pair = new CorrelationPair("outer", "inner");
    assertThat(pair.outerField()).isEqualTo("outer");
    assertThat(pair.innerField()).isEqualTo("inner");
  }

  @Test
  void shouldRejectNullOuterField() {
    assertThatNullPointerException()
        .isThrownBy(() -> new CorrelationPair(null, "inner"))
        .withMessage("outerField must not be null");
  }

  @Test
  void shouldRejectNullInnerField() {
    assertThatNullPointerException()
        .isThrownBy(() -> new CorrelationPair("outer", null))
        .withMessage("innerField must not be null");
  }
}
