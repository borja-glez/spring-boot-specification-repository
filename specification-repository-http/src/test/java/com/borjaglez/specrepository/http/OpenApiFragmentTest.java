package com.borjaglez.specrepository.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.yaml.snakeyaml.Yaml;

import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.core.GroupCondition;
import com.borjaglez.specrepository.core.LogicalOperator;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.core.QueryPlan;

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
  void orFilterGroupIsAnOrOfItsConditionsAndedWithTheFilters() throws IOException {
    Map<String, Object> parameters = map(components(), "parameters");
    Map<String, List<String>> params = new LinkedHashMap<>();
    params.put("filter", example(parameters, "SpecrepoFilter"));
    params.put("orFilter", example(parameters, "SpecrepoOrFilter"));
    params.put("sort", example(parameters, "SpecrepoSort"));

    QueryPlan<Object> plan = new HttpFilterParser().toQueryPlan(Object.class, params);

    assertThat(plan.rootCondition().logicalOperator()).isEqualTo(LogicalOperator.AND);
    assertThat(plan.rootCondition().conditions())
        .containsExactly(
            condition("name", Operators.CONTAINS, "john"),
            condition("age", Operators.GREATER_THAN_OR_EQUAL, "18"),
            new GroupCondition(
                LogicalOperator.OR,
                List.of(
                    condition("status", Operators.EQUALS, "ACTIVE"),
                    condition("status", Operators.EQUALS, "PENDING"))));
    assertThat(plan.sort())
        .isEqualTo(Sort.by(Sort.Order.asc("name"), Sort.Order.desc("createdAt")));
    assertThat(oneLine(map(parameters, "SpecrepoOrFilter").get("description")))
        .contains("the conditions of a group are combined with OR")
        .contains("ANDed with the `filter` conditions and with the other groups");
  }

  @Test
  void parameterPatternsAgreeWithTheParser() throws IOException {
    assertAgreement(
        "SpecrepoFilter",
        List.of(
            "name:eq:john",
            "profile.city:EQ:Madrid",
            "payload:jsonb_eq:{\"a\":1}",
            "deletedAt:isnull",
            "status:in:ACTIVE|PENDING",
            "a_1.b_2:between:1|2",
            "note:eq:a;b"),
        List.of(
            "1name:eq:x",
            "_name:eq:x",
            ".name:eq:x",
            "name.:eq:x",
            "profile..city:eq:x",
            "na-me:eq:x"));
    assertAgreement(
        "SpecrepoOrFilter",
        List.of(
            "name:eq:john",
            "status:eq:ACTIVE;status:EQ:PENDING",
            "payload:jsonb_eq:x;deletedAt:isnull"),
        List.of("status:eq:A;1x:eq:B", "status:eq:A;", "name:eq:a;b", "_x:eq:A;name:eq:B"));
    assertAgreement(
        "SpecrepoSort",
        List.of("name", "name,asc", "profile.city,desc"),
        List.of("_x,asc", "1x", "a..b,desc", "name,"));
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

  private static void assertAgreement(
      String component, List<String> accepted, List<String> rejected) throws IOException {
    Map<String, Object> parameters = map(components(), "parameters");
    Map<String, Object> parameter = map(parameters, component);
    String name = (String) parameter.get("name");
    Pattern pattern =
        Pattern.compile((String) map(map(parameter, "schema"), "items").get("pattern"));
    List<String> valid = new ArrayList<>(accepted);
    valid.addAll(example(parameters, component));
    for (String value : valid) {
      assertThat(parses(name, value)).as("parser accepts %s=%s", name, value).isTrue();
      assertThat(pattern.matcher(value).find()).as("%s accepts %s", component, value).isTrue();
    }
    for (String value : rejected) {
      assertThat(parses(name, value)).as("parser rejects %s=%s", name, value).isFalse();
      assertThat(pattern.matcher(value).find()).as("%s rejects %s", component, value).isFalse();
    }
  }

  private static boolean parses(String name, String value) {
    try {
      new HttpFilterParser().parse(Map.of(name, List.of(value)));
      return true;
    } catch (HttpFilterSyntaxException e) {
      return false;
    }
  }

  private static PredicateCondition condition(String field, FilterOperator operator, Object value) {
    return new PredicateCondition(field, operator, value, false, false);
  }

  @SuppressWarnings("unchecked")
  private static List<String> example(Map<String, Object> parameters, String component) {
    Object example = map(parameters, component).get("example");
    assertThat(example).as("%s example", component).isInstanceOf(List.class);
    return (List<String>) example;
  }

  private static String oneLine(Object text) {
    return String.valueOf(text).replaceAll("\\s+", " ");
  }
}
