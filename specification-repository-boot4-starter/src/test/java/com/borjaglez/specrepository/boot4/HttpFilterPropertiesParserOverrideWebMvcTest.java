package com.borjaglez.specrepository.boot4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.specrepository.http.HttpFilterParser;

/**
 * Boot 4 {@code @WebMvcTest} slice: a user-defined {@link HttpFilterParser} bean wins over the
 * {@code specrepository.http} properties.
 */
@WebMvcTest(
    controllers = HttpFilterPropertiesWebMvcTest.DemoController.class,
    properties = "specrepository.http.max-filters=2")
class HttpFilterPropertiesParserOverrideWebMvcTest {

  @Autowired private MockMvc mvc;

  @Autowired private HttpFilterParser parser;

  @Test
  void shouldUseTheUserDefinedParser() throws Exception {
    assertThat(parser).isSameAs(TestApplication.CUSTOM_PARSER);
    mvc.perform(get("/demo").param("filter", "name:eq:a", "name:eq:b", "name:eq:c"))
        .andExpect(status().isOk())
        .andExpect(content().string("3"));
  }

  @SpringBootConfiguration
  @Import({
    HttpFilterPropertiesWebMvcTest.DemoController.class,
    HttpFilterPropertiesWebMvcTest.FilterErrors.class
  })
  static class TestApplication {

    static final HttpFilterParser CUSTOM_PARSER = new HttpFilterParser();

    @Bean
    HttpFilterParser httpFilterParser() {
      return CUSTOM_PARSER;
    }
  }
}
