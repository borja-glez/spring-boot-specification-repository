package com.borjaglez.specrepository.boot3;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.borjaglez.specrepository.core.InvalidFilterValueException;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.http.HttpFilterSyntaxException;
import com.borjaglez.specrepository.http.spring.FilterableQuery;

/**
 * Boot 3 {@code @WebMvcTest} slice: the auto-configured Problem Details advice yields to the
 * application's handlers and handles only the library's exceptions.
 */
@WebMvcTest(controllers = HttpFilterProblemDetailsWebMvcTest.DemoController.class)
class HttpFilterProblemDetailsWebMvcTest {

  @Autowired private MockMvc mvc;

  @Test
  void shouldAnswerAMalformedFilterWithProblemDetails() throws Exception {
    mvc.perform(get("/demo").param("filter", "name"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Invalid filter expression 'name': missing field name or operator separator"));
  }

  @Test
  void shouldAnswerAnUndeclaredFieldListWithProblemDetails() throws Exception {
    mvc.perform(get("/undeclared").param("filter", "name:eq:x"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.field").value("name"));
  }

  @Test
  void shouldLetTheApplicationHandlerTakePrecedence() throws Exception {
    mvc.perform(get("/app").param("filter", "name"))
        .andExpect(status().isConflict())
        .andExpect(content().string("app: missing field name or operator separator"));
  }

  @Test
  void shouldKeepTheDefaultForTheExceptionsTheApplicationDoesNotHandle() throws Exception {
    mvc.perform(get("/app").param("filter", "secret:eq:x"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Field 'secret' is not allowed for filtering"));
  }

  @Test
  void shouldAnswerAWrappedLibraryExceptionWithProblemDetails() throws Exception {
    mvc.perform(get("/wrapped-library"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail")
                .value("Invalid filter on field 'id': cannot convert 'abc' to Long"))
        .andExpect(jsonPath("$.field").value("id"));
  }

  @Test
  void shouldNotHandleAWrappedExceptionOfAnotherType() {
    assertThatThrownBy(() -> mvc.perform(get("/wrapped-other")))
        .hasCauseInstanceOf(InvalidDataAccessApiUsageException.class)
        .hasRootCauseMessage("not a filter error");
  }

  @Test
  void shouldNotHandleAServerMisconfiguration() {
    assertThatThrownBy(() -> mvc.perform(get("/misconfigured").param("filter", "name:eq:x")))
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  static class Demo {}

  @RestController
  static class DemoController {

    @GetMapping("/demo")
    String demo(
        @FilterableQuery(
                value = Demo.class,
                filterableFields = {"name"})
            QueryPlan<Demo> plan) {
      return String.valueOf(plan.rootCondition().conditions().size());
    }

    @GetMapping("/undeclared")
    String undeclared(@FilterableQuery(Demo.class) QueryPlan<Demo> plan) {
      return String.valueOf(plan.rootCondition().conditions().size());
    }

    @GetMapping("/misconfigured")
    String misconfigured(
        @FilterableQuery(
                value = Demo.class,
                allowAllFields = true,
                filterableFields = {"name"})
            QueryPlan<Demo> plan) {
      return String.valueOf(plan.rootCondition().conditions().size());
    }

    @GetMapping("/wrapped-library")
    String wrappedLibrary() {
      throw new InvalidDataAccessApiUsageException(
          "wrapped",
          new InvalidFilterValueException(
              "id", "abc", Long.class, new NumberFormatException("abc")));
    }

    @GetMapping("/wrapped-other")
    String wrappedOther() {
      throw new InvalidDataAccessApiUsageException(
          "wrapped", new IllegalArgumentException("not a filter error"));
    }
  }

  @RestController
  static class AppController {

    @GetMapping("/app")
    String app(
        @FilterableQuery(
                value = Demo.class,
                filterableFields = {"name"})
            QueryPlan<Demo> plan) {
      return String.valueOf(plan.rootCondition().conditions().size());
    }
  }

  /** An application advice without {@code @Order}, as most applications declare it. */
  @RestControllerAdvice(assignableTypes = AppController.class)
  static class AppFilterErrors {

    @ExceptionHandler(HttpFilterSyntaxException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    String invalidFilter(HttpFilterSyntaxException ex) {
      return "app: " + ex.reason();
    }
  }

  @SpringBootConfiguration
  @Import({DemoController.class, AppController.class, AppFilterErrors.class})
  static class TestApplication {}
}
