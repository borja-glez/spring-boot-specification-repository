package com.borjaglez.specrepository.http.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Guards the configuration metadata generated for {@link HttpFilterProperties}. */
class HttpFilterPropertiesMetadataTest {

  private static final String METADATA = "META-INF/spring-configuration-metadata.json";

  @Test
  void shouldDescribeEveryProperty() throws IOException {
    Map<String, String> descriptions = new HashMap<>();
    for (JsonNode property : readMetadata().get("properties")) {
      descriptions.put(property.get("name").asText(), property.path("description").asText());
    }

    assertThat(descriptions)
        .containsOnlyKeys(
            "specrepository.http.filter-param",
            "specrepository.http.or-filter-param",
            "specrepository.http.sort-param",
            "specrepository.http.multi-value-separator",
            "specrepository.http.or-group-separator",
            "specrepository.http.max-filters",
            "specrepository.http.max-sort-fields",
            "specrepository.http.max-values-per-filter",
            "specrepository.http.max-value-length",
            "specrepository.http.allowed-operators",
            "specrepository.http.problem-details.enabled");
    assertThat(descriptions.values())
        .allSatisfy(description -> assertThat(description).isNotBlank());
  }

  private static JsonNode readMetadata() throws IOException {
    try (InputStream in =
        HttpFilterProperties.class.getClassLoader().getResourceAsStream(METADATA)) {
      assertThat(in).as(METADATA).isNotNull();
      return new ObjectMapper().readTree(in);
    }
  }
}
