package com.borjaglez.specrepository.http.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.http.HttpFilterParser;

/**
 * Boot 3 {@code @WebMvcTest} slice without {@code @ImportAutoConfiguration}: the slice must pick up
 * {@link HttpFilterAutoConfiguration} from the {@code AutoConfigureWebMvc.imports} file.
 */
@WebMvcTest(HttpFilterWebMvcTestSliceTest.DemoController.class)
class HttpFilterWebMvcTestSliceTest {

  @Autowired private MockMvc mvc;

  @Autowired private ApplicationContext context;

  @Test
  void shouldRegisterHttpFilterBeansInTheSlice() {
    assertThat(context.getBeansOfType(HttpFilterParser.class)).hasSize(1);
    assertThat(context.getBeansOfType(QueryPlanArgumentResolver.class)).hasSize(1);
    assertThat(context.getBeansOfType(HttpFilterProblemDetailsExceptionHandler.class)).hasSize(1);
  }

  @Test
  void shouldResolveQueryPlanParameter() throws Exception {
    mvc.perform(get("/demo").param("filter", "name:eq:x"))
        .andExpect(status().isOk())
        .andExpect(content().string("1"));
  }

  @Test
  void shouldAnswerBadRequestWhenTheParameterDeclaresNoFieldLists() throws Exception {
    mvc.perform(get("/undeclared").param("filter", "name:eq:x"))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                assertThat(result.getResolvedException())
                    .isInstanceOf(UndeclaredFieldListException.class)
                    .hasMessageContaining("declares no filterableFields"));
  }

  @Test
  void shouldAnswerAMalformedFilterWithProblemDetails() throws Exception {
    mvc.perform(get("/demo").param("filter", "name"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Invalid filter expression 'name': missing field name or operator separator"))
        .andExpect(jsonPath("$.instance").value("/demo"));
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
  }

  @SpringBootConfiguration
  @Import(DemoController.class)
  static class TestApplication {}
}
