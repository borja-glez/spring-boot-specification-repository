package com.borjaglez.specrepository.http.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.yaml.snakeyaml.Yaml;

import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.http.HttpFilterSyntaxException;

class OpenApiFragmentProblemTest {

  private static final Set<String> STANDARD =
      Set.of("type", "title", "status", "detail", "instance");

  private final HttpFilterProblemDetailsExceptionHandler handler =
      new HttpFilterProblemDetailsExceptionHandler();

  @Test
  void invalidFilterResponseDescribesTheHandlerOutput() throws Exception {
    Map<String, Object> schema = problemSchema();
    @SuppressWarnings("unchecked")
    Set<String> documented =
        new HashSet<>(((Map<String, Object>) schema.get("properties")).keySet());

    ProblemDetail withoutField =
        handler.handleClientFilterError(new HttpFilterSyntaxException("name", "missing operator"));
    ProblemDetail withField =
        handler.handleClientFilterError(new DisallowedFieldException("secret", "sorting"));

    assertThat(documented).containsAll(STANDARD).contains("field");
    assertThat(documented).isEqualTo(written(withField));
    assertThat(written(withoutField)).isEqualTo(STANDARD);
    assertThat(withField.getStatus()).isEqualTo(400);

    @SuppressWarnings("unchecked")
    Object required = schema.get("required");
    assertThat(required == null ? List.<Object>of() : (List<Object>) required)
        .doesNotContain("field");
  }

  private static Set<String> written(ProblemDetail problem) {
    Set<String> names = new HashSet<>(STANDARD);
    if (problem.getProperties() != null) {
      names.addAll(problem.getProperties().keySet());
    }
    return names;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> problemSchema() throws IOException {
    try (InputStream in =
        OpenApiFragmentProblemTest.class.getResourceAsStream(
            "/META-INF/specrepo/openapi/specrepo-filters.yaml")) {
      assertThat(in).isNotNull();
      Map<String, Object> doc = new Yaml().load(in);
      Map<String, Object> components = (Map<String, Object>) doc.get("components");
      Map<String, Object> responses = (Map<String, Object>) components.get("responses");
      Map<String, Object> response = (Map<String, Object>) responses.get("SpecrepoInvalidFilter");
      assertThat(response.get("description")).isNotNull();
      Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
      return (Map<String, Object>) schemas.get("SpecrepoInvalidFilterProblem");
    }
  }
}
