package com.borjaglez.specrepository.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HttpFilterSyntaxExceptionTest {

  @Test
  void shouldExposeRawExpressionAndReason() {
    var ex = new HttpFilterSyntaxException("bad:expr", "missing value");

    assertThat(ex.rawExpression()).isEqualTo("bad:expr");
    assertThat(ex.reason()).isEqualTo("missing value");
    assertThat(ex.getMessage()).isEqualTo("Invalid filter expression 'bad:expr': missing value");
  }
}
