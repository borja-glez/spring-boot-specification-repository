package com.borjaglez.specrepository.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvalidFilterValueExceptionTest {

  @Test
  void shouldExposeFieldValueTargetTypeAndCause() {
    var cause = new NumberFormatException("For input string: \"abc\"");

    var ex = new InvalidFilterValueException("age", "abc", Integer.class, cause);

    assertThat(ex).isInstanceOf(InvalidFilterException.class);
    assertThat(ex.field()).isEqualTo("age");
    assertThat(ex.value()).isEqualTo("abc");
    assertThat(ex.targetType()).isEqualTo(Integer.class);
    assertThat(ex.reason()).isEqualTo("cannot convert 'abc' to Integer");
    assertThat(ex.getMessage())
        .isEqualTo("Invalid filter on field 'age': cannot convert 'abc' to Integer");
    assertThat(ex.getCause()).isSameAs(cause);
  }
}
