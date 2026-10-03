package com.borjaglez.specrepository.examples.boot3;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The {@code Pageable} of {@code GET /api/products/filter} is checked against the sortable fields
 * of its {@code @FilterableQuery}. Spring Data reads the {@code Pageable} sort from {@code orderBy}
 * here, a parameter the filter parser does not read, so only the {@code Pageable} carries it. The
 * repository proxy wraps the {@code DisallowedFieldException} in an {@code
 * InvalidDataAccessApiUsageException}, and the library's Problem Details advice answers 400.
 */
@SpringBootTest(
    classes = Boot3DemoApplication.class,
    properties = "spring.data.web.sort.sort-parameter=orderBy")
@AutoConfigureMockMvc
class ProductFilterPageableSortTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void shouldRejectAPageableSortOutsideTheSortableFields() throws Exception {
    mockMvc
        .perform(get("/api/products/filter").param("orderBy", "category.name,asc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Field 'category.name' is not allowed for sorting"))
        .andExpect(jsonPath("$.field").value("category.name"));
  }

  @Test
  void shouldAcceptAPageableSortOnASortableField() throws Exception {
    mockMvc
        .perform(get("/api/products/filter").param("orderBy", "price,desc"))
        .andExpect(status().isOk());
  }
}
