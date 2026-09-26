package com.borjaglez.specrepository.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class AllowedFieldsPolicyTest {

  @Test
  void allowAllShouldPermitAnyFieldForFiltering() {
    AllowedFieldsPolicy policy = AllowedFieldsPolicy.allowAll();

    policy.validateFilter("anyField");
    policy.validateFilter("nested.path");
  }

  @Test
  void allowAllShouldPermitAnyFieldForSorting() {
    AllowedFieldsPolicy policy = AllowedFieldsPolicy.allowAll();

    policy.validateSort("anyField");
    policy.validateSort("nested.path");
  }

  @Test
  void allowAllShouldReturnTrueForIsAllowAll() {
    assertThat(AllowedFieldsPolicy.allowAll().isAllowAll()).isTrue();
  }

  @Test
  void restrictedPolicyShouldReturnFalseForIsAllowAll() {
    AllowedFieldsPolicy policy = AllowedFieldsPolicy.of(Set.of("name"), Set.of("name"));

    assertThat(policy.isAllowAll()).isFalse();
  }

  @Test
  void shouldAllowWhitelistedFilterFields() {
    AllowedFieldsPolicy policy = AllowedFieldsPolicy.of(Set.of("name", "email"), Set.of("name"));

    policy.validateFilter("name");
    policy.validateFilter("email");
  }

  @Test
  void shouldRejectNonWhitelistedFilterFields() {
    AllowedFieldsPolicy policy = AllowedFieldsPolicy.of(Set.of("name"), Set.of("name"));

    assertThatExceptionOfType(DisallowedFieldException.class)
        .isThrownBy(() -> policy.validateFilter("secretKey"))
        .withMessage("Field 'secretKey' is not allowed for filtering")
        .satisfies(
            ex -> {
              assertThat(ex.field()).isEqualTo("secretKey");
              assertThat(ex.usage()).isEqualTo("filtering");
            });
  }

  @Test
  void shouldAllowWhitelistedSortFields() {
    AllowedFieldsPolicy policy =
        AllowedFieldsPolicy.of(Set.of("name"), Set.of("name", "createdAt"));

    policy.validateSort("name");
    policy.validateSort("createdAt");
  }

  @Test
  void shouldRejectNonWhitelistedSortFields() {
    AllowedFieldsPolicy policy = AllowedFieldsPolicy.of(Set.of("name"), Set.of("name"));

    assertThatExceptionOfType(DisallowedFieldException.class)
        .isThrownBy(() -> policy.validateSort("internalScore"))
        .withMessage("Field 'internalScore' is not allowed for sorting")
        .satisfies(
            ex -> {
              assertThat(ex.field()).isEqualTo("internalScore");
              assertThat(ex.usage()).isEqualTo("sorting");
            });
  }

  @Test
  void shouldReturnSameSingletonForAllowAll() {
    assertThat(AllowedFieldsPolicy.allowAll()).isSameAs(AllowedFieldsPolicy.allowAll());
  }

  @Test
  void shouldRejectNullFilterableSet() {
    assertThatNullPointerException()
        .isThrownBy(() -> AllowedFieldsPolicy.of(null, Set.of()))
        .withMessage("filterable must not be null");
  }

  @Test
  void shouldRejectNullSortableSet() {
    assertThatNullPointerException()
        .isThrownBy(() -> AllowedFieldsPolicy.of(Set.of(), null))
        .withMessage("sortable must not be null");
  }

  // -- validate(plan): the client input of a plan --

  private static final AllowedFieldsPolicy CLIENT =
      AllowedFieldsPolicy.of(Set.of("status", "region"), Set.of("placedAt"));

  private static QueryPlanBuilder<String> clientQuery() {
    return SpecificationQueryBuilder.forEntity(String.class).allowedFields(CLIENT);
  }

  @Test
  void validateShouldAcceptAllowedClientInput() {
    QueryPlan<String> plan =
        clientQuery()
            .where("status", Operators.EQUALS, "PLACED")
            .or(group -> group.where("region", Operators.EQUALS, "north"))
            .exists("lines", sub -> sub.where("sku", Operators.EQUALS, "A"))
            .sort(Sort.by("placedAt"))
            .build();

    assertThatCode(() -> CLIENT.validate(plan)).doesNotThrowAnyException();
  }

  @Test
  void validateShouldAcceptAllowedHavingAndSubqueryOuterFields() {
    QueryPlan<String> plan =
        clientQuery()
            .inSubquery("region", Integer.class, "id", sub -> {})
            .exists(Integer.class, sub -> sub.correlate("status", "status"))
            .groupBy("status")
            .having(AggregateFunction.COUNT, "status", Operators.GREATER_THAN, 1)
            .build();

    assertThatCode(() -> CLIENT.validate(plan)).doesNotThrowAnyException();
  }

  @Test
  void validateShouldRejectADisallowedClientFilter() {
    QueryPlan<String> plan = clientQuery().where("customerId", Operators.EQUALS, "u1").build();

    assertDisallowed(plan, "customerId", "filtering");
  }

  @Test
  void validateShouldRejectADisallowedFilterInANestedGroup() {
    QueryPlan<String> plan =
        clientQuery().or(group -> group.where("customerId", Operators.EQUALS, "u1")).build();

    assertDisallowed(plan, "customerId", "filtering");
  }

  @Test
  void validateShouldNotCheckServerConditions() {
    QueryPlan<String> plan =
        clientQuery().where("status", Operators.EQUALS, "PLACED").build().toBuilder()
            .where("customerId", Operators.EQUALS, "u1")
            .build();

    assertThatCode(() -> CLIENT.validate(plan)).doesNotThrowAnyException();
  }

  @Test
  void validateShouldRejectADisallowedHavingField() {
    QueryPlan<String> plan =
        clientQuery()
            .groupBy("status")
            .having(AggregateFunction.SUM, "total", Operators.GREATER_THAN, 1)
            .build();

    assertDisallowed(plan, "total", "filtering");
  }

  @Test
  void validateShouldRejectADisallowedSort() {
    QueryPlan<String> plan = clientQuery().sort(Sort.by("total")).build();

    assertDisallowed(plan, "total", "sorting");
  }

  @Test
  void validateShouldRejectADisallowedOuterFieldOfAnInSubquery() {
    QueryPlan<String> plan =
        clientQuery().inSubquery("customerId", Integer.class, "id", sub -> {}).build();

    assertDisallowed(plan, "customerId", "filtering");
  }

  @Test
  void validateShouldRejectADisallowedOuterFieldOfANotInSubquery() {
    QueryPlan<String> plan =
        clientQuery().notInSubquery("customerId", Integer.class, "id", sub -> {}).build();

    assertDisallowed(plan, "customerId", "filtering");
  }

  @Test
  void validateShouldRejectADisallowedCorrelatedOuterField() {
    QueryPlan<String> plan =
        clientQuery()
            .exists(Integer.class, sub -> sub.correlate("customerId", "customerId"))
            .build();

    assertDisallowed(plan, "customerId", "filtering");
  }

  @Test
  void validateWithAllowAllShouldAcceptAnyPlan() {
    QueryPlan<String> plan =
        SpecificationQueryBuilder.forEntity(String.class)
            .where("anything", Operators.EQUALS, 1)
            .sort(Sort.by("anything"))
            .build();

    assertThatCode(() -> AllowedFieldsPolicy.allowAll().validate(plan)).doesNotThrowAnyException();
  }

  private static void assertDisallowed(QueryPlan<String> plan, String field, String usage) {
    assertThatExceptionOfType(DisallowedFieldException.class)
        .isThrownBy(() -> CLIENT.validate(plan))
        .satisfies(
            exception -> {
              assertThat(exception.field()).isEqualTo(field);
              assertThat(exception.usage()).isEqualTo(usage);
            });
  }
}
