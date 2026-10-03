package com.borjaglez.specrepository.examples.boot3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The secure controller example of {@code docs/security.md}. */
@SpringBootTest(classes = Boot3DemoApplication.class)
@AutoConfigureMockMvc
class SecureProductControllerTest {

  private static final String URL = "/api/catalog/products";

  @Autowired private MockMvc mockMvc;

  @Autowired private SpringDataWebProperties springDataWebProperties;

  @Test
  void shouldFilterOnlyTheActiveProducts() throws Exception {
    mockMvc
        .perform(get(URL).param("filter", "name:contains:Pro"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.content[0].name").value("MacBook Pro 16"))
        .andExpect(jsonPath("$.content[0].category").value("Electronics"))
        .andExpect(jsonPath("$.content[2].name").value("iPhone 15 Pro"))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  void shouldNotLetAnOrFilterWidenTheServerCondition() throws Exception {
    mockMvc
        .perform(get(URL).param("orFilter", "name:eq:Nokia 3310;name:eq:Clean Code"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Clean Code"));
  }

  @Test
  void shouldReturnASliceWithoutTotal() throws Exception {
    mockMvc
        .perform(get(URL).param("sort", "price,desc").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].name").value("MacBook Pro 16"))
        .andExpect(jsonPath("$.hasNext").value(true));
  }

  @Test
  void shouldMatchLikeWildcardsLiterally() throws Exception {
    mockMvc
        .perform(get(URL).param("filter", "name:contains:%"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  void shouldRejectAFieldOutsideTheFilterableFields() throws Exception {
    expectBadRequest(
        get(URL).param("filter", "status:eq:DISCONTINUED"),
        "Field 'status' is not allowed for filtering");
  }

  @Test
  void shouldRejectASortOutsideTheSortableFields() throws Exception {
    expectBadRequest(
        get(URL).param("sort", "category.name"),
        "Field 'category.name' is not allowed for sorting");
  }

  @Test
  void shouldRejectAMalformedFilter() throws Exception {
    expectBadRequest(get(URL).param("filter", "name"), "missing field name or operator");
  }

  @Test
  void shouldRejectTooManyFilters() throws Exception {
    String[] filters = IntStream.range(0, 21).mapToObj(i -> "price:gt:" + i).toArray(String[]::new);
    expectBadRequest(get(URL).param("filter", filters), "too many filters (max 20)");
  }

  @Test
  void shouldRejectTooManyValues() throws Exception {
    String values = IntStream.range(0, 101).mapToObj(i -> "n" + i).collect(Collectors.joining("|"));
    expectBadRequest(get(URL).param("filter", "name:in:" + values), "too many values (max 100)");
  }

  @Test
  void shouldRejectAnUnknownOperator() throws Exception {
    expectBadRequest(get(URL).param("filter", "name:regex:a"), "unknown operator 'regex'");
  }

  @Test
  void shouldRejectAnUnconvertibleValue() throws Exception {
    expectBadRequest(get(URL).param("filter", "price:gt:abc"), "cannot convert 'abc'");
  }

  @Test
  void shouldCapThePageSize() {
    assertThat(springDataWebProperties.getPageable().getMaxPageSize()).isEqualTo(100);
  }

  @Test
  void shouldMapFilterErrorsOfEveryControllerByDefault() throws Exception {
    // The library's Problem Details advice applies to every controller, not only this one.
    expectBadRequest(
        get("/api/products/filter").param("filter", "name"), "missing field name or operator");
  }

  private void expectBadRequest(MockHttpServletRequestBuilder request, String detail)
      throws Exception {
    mockMvc
        .perform(request)
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value(containsString(detail)));
  }
}
