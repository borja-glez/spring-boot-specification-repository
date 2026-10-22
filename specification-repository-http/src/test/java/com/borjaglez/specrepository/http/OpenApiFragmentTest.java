package com.borjaglez.specrepository.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.core.Operators;

class OpenApiFragmentTest {

  private static final String RESOURCE = "/META-INF/specrepo/openapi/specrepo-filters.yaml";

  @Test
  void fragmentIsACompleteOpenApiDocument() throws IOException {
    Map<String, Object> doc = load();

    assertThat(doc.get("openapi")).isEqualTo("3.0.3");
    assertThat(map(doc, "info")).containsKeys("title", "version", "description");
    assertThat(doc).containsKey("paths");
    assertThat(map(doc, "paths")).isEmpty();
    assertThat(map(doc, "components")).containsKeys("parameters", "schemas", "responses");
  }

  @Test
  void fragmentOperatorsMatchTheParser() throws Exception {
    Set<String> expected = new HashSet<>();
    for (Field field : Operators.class.getFields()) {
      if (Modifier.isStatic(field.getModifiers()) && field.getType() == FilterOperator.class) {
        expected.add(((FilterOperator) field.get(null)).value());
      }
    }
    Map<String, Object> operator = map(map(components(), "schemas"), "SpecrepoFilterOperator");

    assertThat(expected).hasSize(17);
    assertThat(new HashSet<Object>((List<?>) operator.get("enum")))
        .isEqualTo(new HashSet<Object>(expected));
  }

  @Test
  void fragmentParameterNamesMatchTheDefaults() throws IOException {
    HttpFilterParserConfiguration defaults = HttpFilterParserConfiguration.defaults();
    Map<String, Object> parameters = map(components(), "parameters");

    assertThat(map(parameters, "SpecrepoFilter").get("name")).isEqualTo(defaults.filterParam());
    assertThat(map(parameters, "SpecrepoOrFilter").get("name")).isEqualTo(defaults.orFilterParam());
    assertThat(map(parameters, "SpecrepoSort").get("name")).isEqualTo(defaults.sortParam());
  }

  @Test
  void fragmentUsesTheOpenApi30Subset() throws IOException {
    List<String> offences = new ArrayList<>();
    scan(load(), "$", offences);

    assertThat(offences).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private static void scan(Object node, String path, List<String> offences) {
    if (node instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : ((Map<Object, Object>) map).entrySet()) {
        String key = String.valueOf(entry.getKey());
        String child = path + "." + key;
        if ("type".equals(key) && entry.getValue() instanceof List) {
          offences.add(child + " is a type list");
        }
        if (path.contains(".schemas") && ("const".equals(key) || "examples".equals(key))) {
          offences.add(child + " is not valid in OpenAPI 3.0");
        }
        scan(entry.getValue(), child, offences);
      }
    } else if (node instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        scan(list.get(i), path + "[" + i + "]", offences);
      }
    }
  }

  static Map<String, Object> load() throws IOException {
    try (InputStream in = OpenApiFragmentTest.class.getResourceAsStream(RESOURCE)) {
      assertThat(in).as("classpath resource %s", RESOURCE).isNotNull();
      return new Yaml().load(in);
    }
  }

  static Map<String, Object> components() throws IOException {
    return map(load(), "components");
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> map(Map<String, Object> parent, String key) {
    Object value = parent.get(key);
    assertThat(value).as(key).isInstanceOf(Map.class);
    return (Map<String, Object>) value;
  }
}
