package com.borjaglez.specrepository.boot3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.InvalidFilterValueException;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.http.HttpFilterSyntaxException;
import com.borjaglez.specrepository.http.HttpUnknownOperatorException;
import com.borjaglez.specrepository.http.spring.FilterableQuery;

/**
 * Boot 3: the auto-configured advice answers the client errors of the filter API with 400 Problem
 * Details, both when the argument is resolved and when the query runs through the repository proxy
 * (which wraps the exception in an {@link InvalidDataAccessApiUsageException}).
 */
@SpringBootTest(
    classes = {
      ExtensionTestApplication.class,
      HttpFilterProblemDetailsIntegrationTest.ProductController.class
    },
    properties = {
      "spring.datasource.url=jdbc:h2:mem:boot3-problem-details;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.data.web.sort.sort-parameter=orderBy",
      "specrepository.http.allowed-operators=eq"
    })
@AutoConfigureMockMvc
class HttpFilterProblemDetailsIntegrationTest {

  @Autowired private MockMvc mvc;

  @Test
  void shouldAnswerAMalformedFilterWithProblemDetails() throws Exception {
    expectProblem(get("/products").param("filter", "name"), HttpFilterSyntaxException.class)
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Invalid filter expression 'name': missing field name or operator separator"))
        .andExpect(jsonPath("$.field").doesNotExist());
  }

  @Test
  void shouldAnswerAnUnknownOperatorWithProblemDetails() throws Exception {
    expectProblem(
            get("/products").param("filter", "name:regex:a"), HttpUnknownOperatorException.class)
        .andExpect(jsonPath("$.detail").value("Unknown filter operator 'regex'"));
  }

  @Test
  void shouldAnswerADisallowedFieldWithProblemDetails() throws Exception {
    expectProblem(
            get("/products").param("filter", "externalId:eq:x"), DisallowedFieldException.class)
        .andExpect(jsonPath("$.detail").value("Field 'externalId' is not allowed for filtering"))
        .andExpect(jsonPath("$.field").value("externalId"));
  }

  @Test
  void shouldAnswerADisallowedPageableSortWithProblemDetails() throws Exception {
    expectProblem(
            get("/products").param("orderBy", "externalId,asc"),
            InvalidDataAccessApiUsageException.class)
        .andExpect(jsonPath("$.detail").value("Field 'externalId' is not allowed for sorting"))
        .andExpect(jsonPath("$.field").value("externalId"))
        .andExpect(
            result ->
                assertThat(result.getResolvedException())
                    .hasCauseInstanceOf(DisallowedFieldException.class));
  }

  @Test
  void shouldAnswerAnUnconvertibleValueWithProblemDetails() throws Exception {
    expectProblem(
            get("/products").param("filter", "id:eq:abc"), InvalidDataAccessApiUsageException.class)
        .andExpect(
            jsonPath("$.detail")
                .value("Invalid filter on field 'id': cannot convert 'abc' to Long"))
        .andExpect(jsonPath("$.field").value("id"))
        .andExpect(
            result ->
                assertThat(result.getResolvedException())
                    .hasCauseInstanceOf(InvalidFilterValueException.class));
  }

  @Test
  void shouldAnswerAValidRequest() throws Exception {
    mvc.perform(get("/products").param("filter", "name:eq:alpha").param("orderBy", "name,asc"))
        .andExpect(status().isOk())
        .andExpect(content().string("0"));
  }

  private ResultActions expectProblem(
      RequestBuilder request, Class<? extends Exception> resolvedException) throws Exception {
    return mvc.perform(request)
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.instance").value("/products"))
        .andExpect(
            result -> assertThat(result.getResolvedException()).isInstanceOf(resolvedException));
  }

  @RestController
  static class ProductController {

    private final ExtensionTestProductRepository repository;

    ProductController(ExtensionTestProductRepository repository) {
      this.repository = repository;
    }

    @GetMapping("/products")
    long search(
        @FilterableQuery(
                value = ExtensionTestProduct.class,
                filterableFields = {"id", "name"},
                sortableFields = {"name"})
            QueryPlan<ExtensionTestProduct> plan,
        Pageable pageable) {
      return repository.findAll(plan, pageable).getTotalElements();
    }
  }
}
