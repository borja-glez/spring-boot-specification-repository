package com.borjaglez.specrepository.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvalidFilterExceptionTest {

  @Test
  void shouldExposeFieldAndReason() {
    var ex = new InvalidFilterException("status", "unknown operator 'like'");

    assertThat(ex).isInstanceOf(IllegalArgumentException.class);
    assertThat(ex.field()).isEqualTo("status");
    assertThat(ex.reason()).isEqualTo("unknown operator 'like'");
    assertThat(ex.getMessage())
        .isEqualTo("Invalid filter on field 'status': unknown operator 'like'");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void shouldKeepTheCause() {
    var cause = new IllegalArgumentException("Unable to locate Attribute");

    var ex = new InvalidFilterException("profile.nope", "unknown field", cause);

    assertThat(ex.field()).isEqualTo("profile.nope");
    assertThat(ex.reason()).isEqualTo("unknown field");
    assertThat(ex.getMessage()).isEqualTo("Invalid filter on field 'profile.nope': unknown field");
    assertThat(ex.getCause()).isSameAs(cause);
  }
}
