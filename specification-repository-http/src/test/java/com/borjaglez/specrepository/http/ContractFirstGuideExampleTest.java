package com.borjaglez.specrepository.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.GroupCondition;
import com.borjaglez.specrepository.core.LogicalOperator;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.core.QueryPlan;

/**
 * Compiles and exercises the Java code of docs/contract-first.md. The body of {@code ProductSearch}
 * between the snippet markers is the one in the guide, where the class is a top-level
 * {@code @Component} instead of a static nested class.
 */
class ContractFirstGuideExampleTest {

  static class Product {}

  // --- begin snippet: docs/contract-first.md ---
  static class ProductSearch {

    private static final AllowedFieldsPolicy POLICY =
        AllowedFieldsPolicy.of(
            Set.of("name", "status", "category.name"), Set.of("name", "createdAt"));

    private final HttpFilterParser parser;

    ProductSearch(HttpFilterParser parser) {
      this.parser = parser;
    }

    // A generated interface passes null for an optional list that the client did not send.
    QueryPlan<Product> toPlan(List<String> filter, List<String> orFilter, List<String> sort) {
      Map<String, List<String>> params = new LinkedHashMap<>();
      if (filter != null) {
        params.put("filter", filter);
      }
      if (orFilter != null) {
        params.put("orFilter", orFilter);
      }
      if (sort != null) {
        params.put("sort", sort);
      }
      QueryPlan<Product> plan = parser.toQueryPlan(Product.class, params, POLICY, Set.of("name"));
      // The parser does not check the fields: fail now, with a 400, instead of when the query runs.
      POLICY.validate(plan);
      return plan;
    }
  }

  // --- end snippet ---

  private final ProductSearch search = new ProductSearch(new HttpFilterParser());

  @Test
  void shouldBuildAPlanWithFilterOrGroupAndSort() {
    QueryPlan<Product> plan =
        search.toPlan(
            List.of("name:contains:john"),
            List.of("status:eq:ACTIVE;category.name:eq:Books"),
            List.of("createdAt,desc"));

    assertThat(plan.entityType()).isEqualTo(Product.class);
    assertThat(plan.rootCondition().logicalOperator()).isEqualTo(LogicalOperator.AND);
    assertThat(plan.rootCondition().conditions())
        .containsExactly(
            new PredicateCondition("name", Operators.CONTAINS, "john", true, false),
            new GroupCondition(
                LogicalOperator.OR,
                List.of(
                    new PredicateCondition("status", Operators.EQUALS, "ACTIVE", false, false),
                    new PredicateCondition(
                        "category.name", Operators.EQUALS, "Books", false, false))));
    assertThat(plan.sort()).isEqualTo(Sort.by(Sort.Order.desc("createdAt")));
  }

  @Test
  void shouldToleratePartiallyAbsentParameters() {
    QueryPlan<Product> plan = search.toPlan(List.of("status:eq:ACTIVE"), null, null);

    assertThat(plan.rootCondition().conditions()).hasSize(1);
    assertThat(plan.sort().isUnsorted()).isTrue();
  }

  @Test
  void shouldToleratePlainAbsentParameters() {
    QueryPlan<Product> plan = search.toPlan(null, null, null);

    assertThat(plan.rootCondition().conditions()).isEmpty();
    assertThat(plan.sort().isUnsorted()).isTrue();
  }

  @Test
  void shouldRejectAFilterOnAFieldOutsideTheAllowList() {
    assertThatThrownBy(() -> search.toPlan(List.of("passwordHash:eq:x"), null, null))
        .isInstanceOf(DisallowedFieldException.class);
  }

  @Test
  void shouldRejectASortOnAFieldOutsideTheAllowList() {
    assertThatThrownBy(() -> search.toPlan(null, null, List.of("status,asc")))
        .isInstanceOf(DisallowedFieldException.class);
  }
}
