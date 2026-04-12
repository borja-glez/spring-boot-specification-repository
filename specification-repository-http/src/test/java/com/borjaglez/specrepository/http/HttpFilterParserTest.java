package com.borjaglez.specrepository.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.core.LogicalOperator;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.core.QueryPlanBuilder;
import com.borjaglez.specrepository.core.SpecificationQueryBuilder;

class HttpFilterParserTest {

  private final HttpFilterParser parser = new HttpFilterParser();

  // Dummy entity for QueryPlan tests
  static class TestEntity {}

  @Nested
  class ConstructorTests {

    @Test
    void shouldCreateWithDefaults() {
      var p = new HttpFilterParser();
      var result = p.parse(Map.of());
      assertThat(result.filters()).isEmpty();
    }

    @Test
    void shouldRejectNullConfig() {
      assertThatThrownBy(() -> new HttpFilterParser(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class FilterParsingTests {

    @Test
    void shouldParseSingleEqualsFilter() {
      var result = parser.parse(Map.of("filter", List.of("name:eq:John")));

      assertThat(result.filters()).hasSize(1);
      var f = result.filters().get(0);
      assertThat(f.field()).isEqualTo("name");
      assertThat(f.operator()).isEqualTo(Operators.EQUALS);
      assertThat(f.value()).isEqualTo("John");
    }

    @Test
    void shouldParseMultipleFilters() {
      var result = parser.parse(Map.of("filter", List.of("name:eq:John", "status:neq:INACTIVE")));

      assertThat(result.filters()).hasSize(2);
      assertThat(result.filters().get(0).field()).isEqualTo("name");
      assertThat(result.filters().get(1).field()).isEqualTo("status");
    }

    @Test
    void shouldParseNestedPathFilter() {
      var result = parser.parse(Map.of("filter", List.of("category.name:contains:Electronics")));

      assertThat(result.filters().get(0).field()).isEqualTo("category.name");
      assertThat(result.filters().get(0).operator()).isEqualTo(Operators.CONTAINS);
    }

    @Test
    void shouldParseValueWithColons() {
      var result = parser.parse(Map.of("filter", List.of("url:eq:http://example.com:8080")));

      assertThat(result.filters().get(0).value()).isEqualTo("http://example.com:8080");
    }

    @Test
    void shouldReturnEmptyFiltersWhenNoParams() {
      var result = parser.parse(Map.of());

      assertThat(result.filters()).isEmpty();
      assertThat(result.orGroups()).isEmpty();
      assertThat(result.sort()).isEqualTo(Sort.unsorted());
    }

    @Test
    void shouldRejectNullParams() {
      assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectEmptyExpression() {
      assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of(""))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("expression must not be empty");
    }

    @Test
    void shouldRejectMissingOperatorSeparator() {
      assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of("nameonly"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("missing field name or operator separator");
    }

    @Test
    void shouldRejectEmptyFieldName() {
      assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of(":eq:value"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("missing field name or operator separator");
    }

    @Test
    void shouldRejectInvalidFieldName() {
      assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of("../secret:eq:value"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("invalid field name");
    }

    @Test
    void shouldRejectFieldNameStartingWithDot() {
      assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of(".field:eq:value"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("invalid field name");
    }

    @Test
    void shouldRejectFieldNameWithDoubleDot() {
      assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of("a..b:eq:value"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("invalid field name");
    }

    @Test
    void shouldRejectFieldNameWithSpecialChars() {
      assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of("na$me:eq:value"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("invalid field name");
    }

    @Test
    void shouldRejectEmptyOperator() {
      assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of("name::value"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("operator must not be empty");
    }

    @Test
    void shouldRejectMissingValueForNonValuelessOperator() {
      assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of("name:eq"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("value is required for operator 'eq'");
    }

    @Test
    void shouldRejectEmptyValueForNonValuelessOperator() {
      assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of("name:eq:"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("value is required for operator 'eq'");
    }
  }

  @Nested
  class ValuelessOperatorTests {

    @Test
    void shouldParseIsNull() {
      var result = parser.parse(Map.of("filter", List.of("description:isnull")));

      assertThat(result.filters().get(0).operator()).isEqualTo(Operators.IS_NULL);
      assertThat(result.filters().get(0).value()).isNull();
    }

    @Test
    void shouldParseIsNotNull() {
      var result = parser.parse(Map.of("filter", List.of("description:isnotnull")));
      assertThat(result.filters().get(0).operator()).isEqualTo(Operators.IS_NOT_NULL);
      assertThat(result.filters().get(0).value()).isNull();
    }

    @Test
    void shouldParseIsEmpty() {
      var result = parser.parse(Map.of("filter", List.of("tags:isempty")));
      assertThat(result.filters().get(0).operator()).isEqualTo(Operators.IS_EMPTY);
      assertThat(result.filters().get(0).value()).isNull();
    }

    @Test
    void shouldParseIsNotEmpty() {
      var result = parser.parse(Map.of("filter", List.of("tags:isnotempty")));
      assertThat(result.filters().get(0).operator()).isEqualTo(Operators.IS_NOT_EMPTY);
      assertThat(result.filters().get(0).value()).isNull();
    }

    @Test
    void shouldIgnoreValueForValuelessOperator() {
      var result = parser.parse(Map.of("filter", List.of("description:isnull:ignored")));
      assertThat(result.filters().get(0).value()).isNull();
    }
  }

  @Nested
  class MultiValueOperatorTests {

    @Test
    void shouldParseInOperator() {
      var result = parser.parse(Map.of("filter", List.of("status:in:ACTIVE|PENDING")));

      assertThat(result.filters().get(0).operator()).isEqualTo(Operators.IN);
      assertThat(result.filters().get(0).value()).isEqualTo(List.of("ACTIVE", "PENDING"));
    }

    @Test
    void shouldParseNotInOperator() {
      var result = parser.parse(Map.of("filter", List.of("status:notin:DELETED|ARCHIVED")));

      assertThat(result.filters().get(0).operator()).isEqualTo(Operators.NOT_IN);
      assertThat(result.filters().get(0).value()).isEqualTo(List.of("DELETED", "ARCHIVED"));
    }

    @Test
    void shouldParseBetweenOperator() {
      var result = parser.parse(Map.of("filter", List.of("price:between:10|100")));

      assertThat(result.filters().get(0).operator()).isEqualTo(Operators.BETWEEN);
      assertThat(result.filters().get(0).value()).isEqualTo(List.of("10", "100"));
    }

    @Test
    void shouldParseSingleValueForInOperator() {
      var result = parser.parse(Map.of("filter", List.of("status:in:ACTIVE")));
      assertThat(result.filters().get(0).value()).isEqualTo(List.of("ACTIVE"));
    }

    @Test
    void shouldRejectBetweenWithWrongNumberOfValues() {
      assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of("price:between:10"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("requires exactly 2 non-empty values");

      assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of("price:between:10|20|30"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("requires exactly 2 non-empty values");
    }

    @Test
    void shouldRejectBetweenWithEmptyBound() {
      assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of("price:between:|100"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("requires exactly 2 non-empty values");

      assertThatThrownBy(() -> parser.parse(Map.of("filter", List.of("price:between:10|"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("requires exactly 2 non-empty values");
    }
  }

  @Nested
  class OrGroupTests {

    @Test
    void shouldParseSingleOrGroup() {
      var result =
          parser.parse(Map.of("orFilter", List.of("name:contains:John;description:contains:John")));

      assertThat(result.orGroups()).hasSize(1);
      var group = result.orGroups().get(0);
      assertThat(group.filters()).hasSize(2);
      assertThat(group.filters().get(0).field()).isEqualTo("name");
      assertThat(group.filters().get(1).field()).isEqualTo("description");
    }

    @Test
    void shouldParseMultipleOrGroups() {
      var result =
          parser.parse(
              Map.of("orFilter", List.of("name:eq:A;name:eq:B", "status:eq:X;status:eq:Y")));

      assertThat(result.orGroups()).hasSize(2);
    }

    @Test
    void shouldCombineFilterAndOrGroupCounts() {
      var config = HttpFilterParserConfiguration.builder().maxFilters(3).build();
      var p = new HttpFilterParser(config);

      assertThatThrownBy(
              () ->
                  p.parse(
                      Map.of(
                          "filter",
                          List.of("a:eq:1", "b:eq:2"),
                          "orFilter",
                          List.of("c:eq:3;d:eq:4"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("too many filters");
    }
  }

  @Nested
  class SortTests {

    @Test
    void shouldParseSortWithAscDirection() {
      var result = parser.parse(Map.of("sort", List.of("name,asc")));

      assertThat(result.sort()).isEqualTo(Sort.by(Sort.Order.asc("name")));
    }

    @Test
    void shouldParseSortWithDescDirection() {
      var result = parser.parse(Map.of("sort", List.of("price,desc")));

      assertThat(result.sort()).isEqualTo(Sort.by(Sort.Order.desc("price")));
    }

    @Test
    void shouldDefaultToAscWhenDirectionOmitted() {
      var result = parser.parse(Map.of("sort", List.of("name")));

      assertThat(result.sort()).isEqualTo(Sort.by(Sort.Order.asc("name")));
    }

    @Test
    void shouldParseMultipleSortFields() {
      var result = parser.parse(Map.of("sort", List.of("name,asc", "price,desc")));

      assertThat(result.sort())
          .isEqualTo(Sort.by(Sort.Order.asc("name"), Sort.Order.desc("price")));
    }

    @Test
    void shouldBeCaseInsensitiveForDirection() {
      var result = parser.parse(Map.of("sort", List.of("name,ASC", "price,DESC")));

      assertThat(result.sort())
          .isEqualTo(Sort.by(Sort.Order.asc("name"), Sort.Order.desc("price")));
    }

    @Test
    void shouldRejectInvalidSortDirection() {
      assertThatThrownBy(() -> parser.parse(Map.of("sort", List.of("name,invalid"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("invalid sort direction");
    }

    @Test
    void shouldRejectEmptySortExpression() {
      assertThatThrownBy(() -> parser.parse(Map.of("sort", List.of(""))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("sort expression must not be empty");
    }

    @Test
    void shouldRejectEmptySortField() {
      assertThatThrownBy(() -> parser.parse(Map.of("sort", List.of(",asc"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("sort field must not be empty");
    }

    @Test
    void shouldRejectSortWithExtraCommas() {
      assertThatThrownBy(() -> parser.parse(Map.of("sort", List.of("name,asc,extra"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("sort expression must have at most 2 parts");
    }

    @Test
    void shouldRejectInvalidSortFieldName() {
      assertThatThrownBy(() -> parser.parse(Map.of("sort", List.of("../hack,asc"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("invalid field name");
    }

    @Test
    void shouldRejectTooManySortFields() {
      var config = HttpFilterParserConfiguration.builder().maxSortFields(2).build();
      var p = new HttpFilterParser(config);

      assertThatThrownBy(() -> p.parse(Map.of("sort", List.of("a,asc", "b,asc", "c,asc"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("too many sort fields");
    }

    @Test
    void shouldReturnUnsortedWhenNoSortParam() {
      var result = parser.parse(Map.of());
      assertThat(result.sort().isUnsorted()).isTrue();
    }
  }

  @Nested
  class OperatorValidationTests {

    @Test
    void shouldRejectUnknownOperatorWhenAllowedOperatorsConfigured() {
      var config =
          HttpFilterParserConfiguration.builder().allowedOperators(Set.of("eq", "neq")).build();
      var p = new HttpFilterParser(config);

      assertThatThrownBy(() -> p.parse(Map.of("filter", List.of("name:contains:John"))))
          .isInstanceOf(HttpUnknownOperatorException.class)
          .hasMessageContaining("contains");
    }

    @Test
    void shouldAcceptOperatorInAllowedSet() {
      var config = HttpFilterParserConfiguration.builder().allowedOperators(Set.of("eq")).build();
      var p = new HttpFilterParser(config);

      var result = p.parse(Map.of("filter", List.of("name:eq:John")));
      assertThat(result.filters()).hasSize(1);
    }

    @Test
    void shouldAcceptAnyOperatorWhenAllowedOperatorsIsNull() {
      var result = parser.parse(Map.of("filter", List.of("name:customop:value")));
      assertThat(result.filters().get(0).operator()).isEqualTo(FilterOperator.of("customop"));
    }

    @Test
    void shouldAcceptOperatorsConfiguredInUpperCase() {
      var config = HttpFilterParserConfiguration.builder().allowedOperators(Set.of("EQ")).build();
      var p = new HttpFilterParser(config);

      var result = p.parse(Map.of("filter", List.of("name:eq:John")));
      assertThat(result.filters()).hasSize(1);
    }
  }

  @Nested
  class MaxFiltersTests {

    @Test
    void shouldRejectTooManyFilters() {
      var config = HttpFilterParserConfiguration.builder().maxFilters(2).build();
      var p = new HttpFilterParser(config);

      assertThatThrownBy(() -> p.parse(Map.of("filter", List.of("a:eq:1", "b:eq:2", "c:eq:3"))))
          .isInstanceOf(HttpFilterSyntaxException.class)
          .hasMessageContaining("too many filters");
    }

    @Test
    void shouldAcceptExactlyMaxFilters() {
      var config = HttpFilterParserConfiguration.builder().maxFilters(2).build();
      var p = new HttpFilterParser(config);

      var result = p.parse(Map.of("filter", List.of("a:eq:1", "b:eq:2")));
      assertThat(result.filters()).hasSize(2);
    }
  }

  @Nested
  class ApplyToTests {

    @Test
    void shouldApplyFiltersToBuilder() {
      QueryPlanBuilder<TestEntity> builder = SpecificationQueryBuilder.forEntity(TestEntity.class);

      parser.applyTo(builder, Map.of("filter", List.of("name:eq:John")));

      QueryPlan<TestEntity> plan = builder.build();
      assertThat(plan.rootCondition().conditions()).hasSize(1);
      var cond = (PredicateCondition) plan.rootCondition().conditions().get(0);
      assertThat(cond.field()).isEqualTo("name");
      assertThat(cond.operator()).isEqualTo(Operators.EQUALS);
      assertThat(cond.value()).isEqualTo("John");
    }

    @Test
    void shouldApplyOrGroupsToBuilder() {
      QueryPlanBuilder<TestEntity> builder = SpecificationQueryBuilder.forEntity(TestEntity.class);

      parser.applyTo(builder, Map.of("orFilter", List.of("name:eq:A;name:eq:B")));

      QueryPlan<TestEntity> plan = builder.build();
      assertThat(plan.rootCondition().conditions()).hasSize(1);
      var orGroup =
          (com.borjaglez.specrepository.core.GroupCondition)
              plan.rootCondition().conditions().get(0);
      assertThat(orGroup.logicalOperator()).isEqualTo(LogicalOperator.OR);
      assertThat(orGroup.conditions()).hasSize(2);
    }

    @Test
    void shouldApplySortToBuilder() {
      QueryPlanBuilder<TestEntity> builder = SpecificationQueryBuilder.forEntity(TestEntity.class);

      parser.applyTo(builder, Map.of("sort", List.of("name,desc")));

      QueryPlan<TestEntity> plan = builder.build();
      assertThat(plan.sort()).isEqualTo(Sort.by(Sort.Order.desc("name")));
    }

    @Test
    void shouldNotApplyUnsorted() {
      QueryPlanBuilder<TestEntity> builder = SpecificationQueryBuilder.forEntity(TestEntity.class);
      builder.sort(Sort.by("existing"));

      parser.applyTo(builder, Map.of());

      QueryPlan<TestEntity> plan = builder.build();
      assertThat(plan.sort()).isEqualTo(Sort.by("existing"));
    }

    @Test
    void shouldRejectNullBuilder() {
      assertThatThrownBy(() -> parser.applyTo(null, Map.of()))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class ToQueryPlanTests {

    @Test
    void shouldBuildQueryPlanFromParams() {
      QueryPlan<TestEntity> plan =
          parser.toQueryPlan(
              TestEntity.class,
              Map.of("filter", List.of("name:eq:John"), "sort", List.of("name,asc")));

      assertThat(plan.entityType()).isEqualTo(TestEntity.class);
      assertThat(plan.rootCondition().conditions()).hasSize(1);
      assertThat(plan.sort()).isEqualTo(Sort.by(Sort.Order.asc("name")));
    }

    @Test
    void shouldBuildQueryPlanWithAllowedFieldsPolicy() {
      AllowedFieldsPolicy policy = AllowedFieldsPolicy.of(Set.of("name"), Set.of("name"));

      QueryPlan<TestEntity> plan =
          parser.toQueryPlan(TestEntity.class, Map.of("filter", List.of("name:eq:John")), policy);

      assertThat(plan.allowedFieldsPolicy()).isSameAs(policy);
    }

    @Test
    void shouldRejectNullEntityType() {
      assertThatThrownBy(() -> parser.toQueryPlan(null, Map.of()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullEntityTypeWithPolicy() {
      assertThatThrownBy(() -> parser.toQueryPlan(null, Map.of(), AllowedFieldsPolicy.allowAll()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullPolicy() {
      assertThatThrownBy(() -> parser.toQueryPlan(TestEntity.class, Map.of(), null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldBuildEmptyQueryPlan() {
      QueryPlan<TestEntity> plan = parser.toQueryPlan(TestEntity.class, Map.of());

      assertThat(plan.entityType()).isEqualTo(TestEntity.class);
      assertThat(plan.rootCondition().conditions()).isEmpty();
      assertThat(plan.sort().isUnsorted()).isTrue();
    }
  }

  @Nested
  class CustomConfigTests {

    @Test
    void shouldUseCustomParameterNames() {
      var config =
          HttpFilterParserConfiguration.builder()
              .filterParam("f")
              .orFilterParam("of")
              .sortParam("s")
              .build();
      var p = new HttpFilterParser(config);

      var result =
          p.parse(
              Map.of(
                  "f", List.of("name:eq:John"),
                  "of", List.of("a:eq:1;b:eq:2"),
                  "s", List.of("name,desc")));

      assertThat(result.filters()).hasSize(1);
      assertThat(result.orGroups()).hasSize(1);
      assertThat(result.sort()).isEqualTo(Sort.by(Sort.Order.desc("name")));
    }

    @Test
    void shouldUseCustomMultiValueSeparator() {
      var config = HttpFilterParserConfiguration.builder().multiValueSeparator(",").build();
      var p = new HttpFilterParser(config);

      var result = p.parse(Map.of("filter", List.of("status:in:ACTIVE,PENDING")));

      assertThat(result.filters().get(0).value()).isEqualTo(List.of("ACTIVE", "PENDING"));
    }

    @Test
    void shouldUseCustomOrGroupSeparator() {
      var config = HttpFilterParserConfiguration.builder().orGroupSeparator("&").build();
      var p = new HttpFilterParser(config);

      var result = p.parse(Map.of("orFilter", List.of("a:eq:1&b:eq:2")));

      assertThat(result.orGroups().get(0).filters()).hasSize(2);
    }
  }

  @Nested
  class AllOperatorsTests {

    @Test
    void shouldParseAllStringOperators() {
      var operators = List.of("contains", "notcontains", "startswith", "endswith");
      for (String op : operators) {
        var result = parser.parse(Map.of("filter", List.of("name:" + op + ":test")));
        assertThat(result.filters().get(0).operator()).isEqualTo(FilterOperator.of(op));
      }
    }

    @Test
    void shouldParseAllComparisonOperators() {
      var operators = List.of("gt", "gte", "lt", "lte");
      for (String op : operators) {
        var result = parser.parse(Map.of("filter", List.of("price:" + op + ":100")));
        assertThat(result.filters().get(0).operator()).isEqualTo(FilterOperator.of(op));
      }
    }
  }
}
