package com.borjaglez.specrepository.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HttpUnknownOperatorExceptionTest {

  @Test
  void shouldExposeOperator() {
    var ex = new HttpUnknownOperatorException("badop");

    assertThat(ex.operator()).isEqualTo("badop");
    assertThat(ex.getMessage()).isEqualTo("Unknown filter operator 'badop'");
  }
}
