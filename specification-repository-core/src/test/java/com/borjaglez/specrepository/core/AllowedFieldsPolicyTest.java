package com.borjaglez.specrepository.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Set;

import org.junit.jupiter.api.Test;

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
}
