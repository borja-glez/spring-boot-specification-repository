package com.borjaglez.specrepository.http.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.borjaglez.specrepository.http.HttpFilterParserConfiguration;

class HttpFilterPropertiesTest {

  @Test
  void shouldDefaultToTheParserDefaults() {
    HttpFilterProperties properties = new HttpFilterProperties();
    HttpFilterParserConfiguration defaults = HttpFilterParserConfiguration.defaults();

    assertThat(properties.getFilterParam()).isEqualTo(defaults.filterParam());
    assertThat(properties.getOrFilterParam()).isEqualTo(defaults.orFilterParam());
    assertThat(properties.getSortParam()).isEqualTo(defaults.sortParam());
    assertThat(properties.getMultiValueSeparator()).isEqualTo(defaults.multiValueSeparator());
    assertThat(properties.getOrGroupSeparator()).isEqualTo(defaults.orGroupSeparator());
    assertThat(properties.getMaxFilters()).isEqualTo(defaults.maxFilters());
    assertThat(properties.getMaxSortFields()).isEqualTo(defaults.maxSortFields());
    assertThat(properties.getMaxValuesPerFilter()).isEqualTo(defaults.maxValuesPerFilter());
    assertThat(properties.getMaxValueLength()).isEqualTo(defaults.maxValueLength());
    assertThat(properties.getAllowedOperators()).isEmpty();
    assertThat(properties.getProblemDetails().isEnabled()).isTrue();
  }

  @Test
  void shouldDisableProblemDetails() {
    HttpFilterProperties properties = new HttpFilterProperties();
    properties.getProblemDetails().setEnabled(false);

    assertThat(properties.getProblemDetails().isEnabled()).isFalse();
  }

  @Test
  void shouldMapDefaultsToTheDefaultParserConfiguration() {
    HttpFilterParserConfiguration config = new HttpFilterProperties().toParserConfiguration();
    HttpFilterParserConfiguration defaults = HttpFilterParserConfiguration.defaults();

    assertThat(config).usingRecursiveComparison().isEqualTo(defaults);
    assertThat(config.allowedOperators()).isNull();
  }

  @Test
  void shouldMapEveryProperty() {
    HttpFilterProperties properties = new HttpFilterProperties();
    properties.setFilterParam("q");
    properties.setOrFilterParam("any");
    properties.setSortParam("order");
    properties.setMultiValueSeparator(",");
    properties.setOrGroupSeparator("|");
    properties.setMaxFilters(10);
    properties.setMaxSortFields(3);
    properties.setMaxValuesPerFilter(50);
    properties.setMaxValueLength(200);
    properties.setAllowedOperators(new LinkedHashSet<>(List.of("EQ", "contains")));

    assertThat(properties.getFilterParam()).isEqualTo("q");
    assertThat(properties.getOrFilterParam()).isEqualTo("any");
    assertThat(properties.getSortParam()).isEqualTo("order");
    assertThat(properties.getMultiValueSeparator()).isEqualTo(",");
    assertThat(properties.getOrGroupSeparator()).isEqualTo("|");
    assertThat(properties.getMaxFilters()).isEqualTo(10);
    assertThat(properties.getMaxSortFields()).isEqualTo(3);
    assertThat(properties.getMaxValuesPerFilter()).isEqualTo(50);
    assertThat(properties.getMaxValueLength()).isEqualTo(200);
    assertThat(properties.getAllowedOperators()).containsExactly("EQ", "contains");

    HttpFilterParserConfiguration config = properties.toParserConfiguration();

    assertThat(config.filterParam()).isEqualTo("q");
    assertThat(config.orFilterParam()).isEqualTo("any");
    assertThat(config.sortParam()).isEqualTo("order");
    assertThat(config.multiValueSeparator()).isEqualTo(",");
    assertThat(config.orGroupSeparator()).isEqualTo("|");
    assertThat(config.maxFilters()).isEqualTo(10);
    assertThat(config.maxSortFields()).isEqualTo(3);
    assertThat(config.maxValuesPerFilter()).isEqualTo(50);
    assertThat(config.maxValueLength()).isEqualTo(200);
    assertThat(config.allowedOperators()).isEqualTo(Set.of("eq", "contains"));
  }

  @Test
  void shouldApplyTheBuilderValidation() {
    HttpFilterProperties properties = new HttpFilterProperties();
    properties.setMaxFilters(0);

    assertThatThrownBy(properties::toParserConfiguration)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maxFilters must be at least 1");
  }
}
