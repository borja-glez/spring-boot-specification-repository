package com.borjaglez.specrepository.boot4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ControllerAdvice;

import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.http.HttpFilterSyntaxException;

/**
 * Boot 4 {@code @WebMvcTest} slice with {@code specrepository.http.problem-details.enabled=false}:
 * the advice is not registered and the previous behaviour returns.
 */
@WebMvcTest(
    controllers = HttpFilterProblemDetailsWebMvcTest.DemoController.class,
    properties = "specrepository.http.problem-details.enabled=false")
class HttpFilterProblemDetailsDisabledWebMvcTest {

  @Autowired private MockMvc mvc;

  @Autowired private ApplicationContext context;

  @Test
  void shouldNotRegisterTheAdvice() {
    assertThat(context.getBeansWithAnnotation(ControllerAdvice.class)).isEmpty();
  }

  @Test
  void shouldLeaveAMalformedFilterUnhandled() {
    assertThatThrownBy(() -> mvc.perform(get("/demo").param("filter", "name")))
        .hasCauseInstanceOf(HttpFilterSyntaxException.class);
  }

  @Test
  void shouldLeaveADisallowedFieldUnhandled() {
    assertThatThrownBy(() -> mvc.perform(get("/demo").param("filter", "secret:eq:x")))
        .hasCauseInstanceOf(DisallowedFieldException.class);
  }

  @Test
  void shouldKeepTheResponseStatusOfAnUndeclaredFieldList() throws Exception {
    mvc.perform(get("/undeclared").param("filter", "name:eq:x"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(""));
  }

  @SpringBootConfiguration
  @Import(HttpFilterProblemDetailsWebMvcTest.DemoController.class)
  static class TestApplication {}
}
