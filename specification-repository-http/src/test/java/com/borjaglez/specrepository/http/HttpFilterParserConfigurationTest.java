package com.borjaglez.specrepository.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

class HttpFilterParserConfigurationTest {

  @Test
  void defaultsShouldHaveExpectedValues() {
    var config = HttpFilterParserConfiguration.defaults();

    assertThat(config.filterParam()).isEqualTo("filter");
    assertThat(config.orFilterParam()).isEqualTo("orFilter");
    assertThat(config.sortParam()).isEqualTo("sort");
    assertThat(config.multiValueSeparator()).isEqualTo("|");
    assertThat(config.orGroupSeparator()).isEqualTo(";");
    assertThat(config.maxFilters()).isEqualTo(20);
    assertThat(config.maxSortFields()).isEqualTo(5);
    assertThat(config.allowedOperators()).isNull();
  }

  @Test
  void builderShouldOverrideAllValues() {
    var config =
        HttpFilterParserConfiguration.builder()
            .filterParam("f")
            .orFilterParam("of")
            .sortParam("s")
            .multiValueSeparator(",")
            .orGroupSeparator("&")
            .maxFilters(10)
            .maxSortFields(3)
            .allowedOperators(Set.of("eq", "neq"))
            .build();

    assertThat(config.filterParam()).isEqualTo("f");
    assertThat(config.orFilterParam()).isEqualTo("of");
    assertThat(config.sortParam()).isEqualTo("s");
    assertThat(config.multiValueSeparator()).isEqualTo(",");
    assertThat(config.orGroupSeparator()).isEqualTo("&");
    assertThat(config.maxFilters()).isEqualTo(10);
    assertThat(config.maxSortFields()).isEqualTo(3);
    assertThat(config.allowedOperators()).containsExactlyInAnyOrder("eq", "neq");
  }

  @Test
  void builderShouldRejectNullFilterParam() {
    assertThatThrownBy(() -> HttpFilterParserConfiguration.builder().filterParam(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void builderShouldRejectNullOrFilterParam() {
    assertThatThrownBy(() -> HttpFilterParserConfiguration.builder().orFilterParam(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void builderShouldRejectNullSortParam() {
    assertThatThrownBy(() -> HttpFilterParserConfiguration.builder().sortParam(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void builderShouldRejectNullMultiValueSeparator() {
    assertThatThrownBy(() -> HttpFilterParserConfiguration.builder().multiValueSeparator(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void builderShouldRejectNullOrGroupSeparator() {
    assertThatThrownBy(() -> HttpFilterParserConfiguration.builder().orGroupSeparator(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void builderShouldRejectZeroMaxFilters() {
    assertThatThrownBy(() -> HttpFilterParserConfiguration.builder().maxFilters(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxFilters must be at least 1");
  }

  @Test
  void builderShouldRejectZeroMaxSortFields() {
    assertThatThrownBy(() -> HttpFilterParserConfiguration.builder().maxSortFields(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxSortFields must be at least 1");
  }

  @Test
  void allowedOperatorsShouldBeImmutableCopy() {
    var operators = new java.util.HashSet<>(Set.of("eq", "neq"));
    var config = HttpFilterParserConfiguration.builder().allowedOperators(operators).build();
    operators.add("gt");
    assertThat(config.allowedOperators()).containsExactlyInAnyOrder("eq", "neq");
  }
}
