package com.borjaglez.specrepository.http.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.InvalidFilterException;
import com.borjaglez.specrepository.core.InvalidFilterValueException;
import com.borjaglez.specrepository.http.HttpFilterSyntaxException;
import com.borjaglez.specrepository.http.HttpUnknownOperatorException;

class HttpFilterProblemDetailsExceptionHandlerTest {

  private final HttpFilterProblemDetailsExceptionHandler handler =
      new HttpFilterProblemDetailsExceptionHandler();

  @Test
  void shouldMapASyntaxErrorWithoutAField() throws Exception {
    ProblemDetail problem =
        handler.handleClientFilterError(new HttpFilterSyntaxException("name", "missing operator"));

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getDetail()).isEqualTo("Invalid filter expression 'name': missing operator");
    assertThat(problem.getProperties()).isNull();
  }

  @Test
  void shouldMapAnUnknownOperatorWithoutAField() throws Exception {
    ProblemDetail problem =
        handler.handleClientFilterError(new HttpUnknownOperatorException("regex"));

    assertThat(problem.getStatus()).isEqualTo(400);
    assertThat(problem.getDetail()).isEqualTo("Unknown filter operator 'regex'");
    assertThat(problem.getProperties()).isNull();
  }

  @Test
  void shouldMapADisallowedFieldWithItsField() throws Exception {
    ProblemDetail problem =
        handler.handleClientFilterError(new DisallowedFieldException("secret", "sorting"));

    assertThat(problem.getStatus()).isEqualTo(400);
    assertThat(problem.getDetail()).isEqualTo("Field 'secret' is not allowed for sorting");
    assertThat(problem.getProperties()).containsEntry("field", "secret");
  }

  @Test
  void shouldMapAnUndeclaredFieldListWithItsField() throws Exception {
    ProblemDetail problem =
        handler.handleClientFilterError(
            new UndeclaredFieldListException("name", "filtering", "filterableFields"));

    assertThat(problem.getStatus()).isEqualTo(400);
    assertThat(problem.getDetail()).contains("declares no filterableFields");
    assertThat(problem.getProperties()).containsEntry("field", "name");
  }

  @Test
  void shouldMapAnInvalidFilterWithItsField() throws Exception {
    ProblemDetail problem =
        handler.handleClientFilterError(new InvalidFilterException("missing", "unknown field"));

    assertThat(problem.getStatus()).isEqualTo(400);
    assertThat(problem.getDetail()).isEqualTo("Invalid filter on field 'missing': unknown field");
    assertThat(problem.getProperties()).containsEntry("field", "missing");
  }

  @Test
  void shouldMapAWrappedInvalidFilterValueWithItsField() throws Exception {
    InvalidFilterValueException cause =
        new InvalidFilterValueException(
            "price", "abc", Integer.class, new NumberFormatException("abc"));

    ProblemDetail problem =
        handler.handleClientFilterError(new IllegalStateException("wrapper", cause));

    assertThat(problem.getStatus()).isEqualTo(400);
    assertThat(problem.getDetail())
        .isEqualTo("Invalid filter on field 'price': cannot convert 'abc' to Integer");
    assertThat(problem.getProperties()).containsEntry("field", "price");
  }

  @Test
  void shouldMapTheLibraryExceptionDeepInTheCauseChain() throws Exception {
    DisallowedFieldException cause = new DisallowedFieldException("secret", "filtering");

    ProblemDetail problem =
        handler.handleClientFilterError(
            new RuntimeException("outer", new RuntimeException("inner", cause)));

    assertThat(problem.getDetail()).isEqualTo(cause.getMessage());
    assertThat(problem.getProperties()).containsEntry("field", "secret");
  }

  @Test
  void shouldRethrowAnExceptionWithoutALibraryExceptionInItsCauseChain() {
    IllegalArgumentException exception =
        new IllegalArgumentException("not a filter error", new IllegalStateException("cause"));

    assertThatThrownBy(() -> handler.handleClientFilterError(exception)).isSameAs(exception);
  }

  @Test
  void shouldHandleOnlyTheLibraryExceptionTypes() throws Exception {
    ExceptionHandler annotation =
        HttpFilterProblemDetailsExceptionHandler.class
            .getMethod("handleClientFilterError", Exception.class)
            .getAnnotation(ExceptionHandler.class);

    assertThat(annotation.value())
        .containsExactlyInAnyOrder(
            HttpFilterSyntaxException.class,
            HttpUnknownOperatorException.class,
            DisallowedFieldException.class,
            InvalidFilterException.class);
  }

  @Test
  void shouldHaveTheLowestPrecedence() {
    assertThat(OrderUtils.getOrder(HttpFilterProblemDetailsExceptionHandler.class))
        .isEqualTo(Ordered.LOWEST_PRECEDENCE);
  }
}
