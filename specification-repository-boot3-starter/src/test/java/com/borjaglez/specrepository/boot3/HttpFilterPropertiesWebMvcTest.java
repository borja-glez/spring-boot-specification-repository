package com.borjaglez.specrepository.boot3;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.http.HttpFilterSyntaxException;
import com.borjaglez.specrepository.http.spring.FilterableQuery;

/**
 * Boot 3 {@code @WebMvcTest} slice: the auto-configured {@code HttpFilterParser} is built from the
 * {@code specrepository.http} properties.
 */
@WebMvcTest(
    controllers = HttpFilterPropertiesWebMvcTest.DemoController.class,
    properties = {"specrepository.http.max-filters=2", "specrepository.http.filter-param=q"})
class HttpFilterPropertiesWebMvcTest {

  @Autowired private MockMvc mvc;

  @Test
  void shouldAnswerBadRequestAboveTheConfiguredMaxFilters() throws Exception {
    mvc.perform(get("/demo").param("q", "name:eq:a", "name:eq:b", "name:eq:c"))
        .andExpect(status().isBadRequest())
        .andExpect(
            content().string("Invalid filter expression '3 filters': too many filters (max 2)"));
  }

  @Test
  void shouldAcceptFiltersUpToTheConfiguredMaxFilters() throws Exception {
    mvc.perform(get("/demo").param("q", "name:eq:a", "name:eq:b"))
        .andExpect(status().isOk())
        .andExpect(content().string("2"));
  }

  @Test
  void shouldReadFiltersFromTheConfiguredParameterName() throws Exception {
    mvc.perform(get("/demo").param("filter", "name:eq:a"))
        .andExpect(status().isOk())
        .andExpect(content().string("0"));
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
  }

  @RestControllerAdvice
  static class FilterErrors {

    @ExceptionHandler(HttpFilterSyntaxException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String invalidFilter(HttpFilterSyntaxException ex) {
      return ex.getMessage();
    }
  }

  @SpringBootConfiguration
  @Import({DemoController.class, FilterErrors.class})
  static class TestApplication {}
}
