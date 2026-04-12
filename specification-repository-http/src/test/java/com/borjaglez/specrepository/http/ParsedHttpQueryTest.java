package com.borjaglez.specrepository.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.http.ParsedHttpQuery.ParsedFilter;
import com.borjaglez.specrepository.http.ParsedHttpQuery.ParsedOrGroup;

class ParsedHttpQueryTest {

  @Test
  void shouldCreateImmutableQuery() {
    var filter = new ParsedFilter("name", FilterOperator.of("eq"), "John");
    var orGroup = new ParsedOrGroup(List.of(filter));
    var query = new ParsedHttpQuery(List.of(filter), List.of(orGroup), Sort.unsorted());

    assertThat(query.filters()).hasSize(1);
    assertThat(query.orGroups()).hasSize(1);
    assertThat(query.sort()).isEqualTo(Sort.unsorted());
  }

  @Test
  void shouldRejectNullFilters() {
    assertThatThrownBy(() -> new ParsedHttpQuery(null, List.of(), Sort.unsorted()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldRejectNullOrGroups() {
    assertThatThrownBy(() -> new ParsedHttpQuery(List.of(), null, Sort.unsorted()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldRejectNullSort() {
    assertThatThrownBy(() -> new ParsedHttpQuery(List.of(), List.of(), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void parsedFilterShouldRejectNullField() {
    assertThatThrownBy(() -> new ParsedFilter(null, FilterOperator.of("eq"), "v"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void parsedFilterShouldRejectNullOperator() {
    assertThatThrownBy(() -> new ParsedFilter("f", null, "v"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void parsedFilterShouldAllowNullValue() {
    var filter = new ParsedFilter("f", FilterOperator.of("isnull"), null);
    assertThat(filter.value()).isNull();
  }

  @Test
  void parsedOrGroupShouldRejectNullFilters() {
    assertThatThrownBy(() -> new ParsedOrGroup(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldReturnDefensiveCopies() {
    var filter = new ParsedFilter("name", FilterOperator.of("eq"), "John");
    var query = new ParsedHttpQuery(List.of(filter), List.of(), Sort.unsorted());

    assertThatThrownBy(() -> query.filters().add(filter))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> query.orGroups().add(new ParsedOrGroup(List.of())))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void parsedOrGroupShouldReturnDefensiveCopy() {
    var filter = new ParsedFilter("name", FilterOperator.of("eq"), "John");
    var group = new ParsedOrGroup(List.of(filter));

    assertThatThrownBy(() -> group.filters().add(filter))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
