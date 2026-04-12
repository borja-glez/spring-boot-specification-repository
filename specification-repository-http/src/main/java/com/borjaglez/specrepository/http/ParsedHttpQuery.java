package com.borjaglez.specrepository.http;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Sort;

import com.borjaglez.specrepository.core.FilterOperator;

public record ParsedHttpQuery(List<ParsedFilter> filters, List<ParsedOrGroup> orGroups, Sort sort) {

  public ParsedHttpQuery {
    Objects.requireNonNull(filters, "filters must not be null");
    Objects.requireNonNull(orGroups, "orGroups must not be null");
    Objects.requireNonNull(sort, "sort must not be null");
    filters = List.copyOf(filters);
    orGroups = List.copyOf(orGroups);
  }

  public record ParsedFilter(String field, FilterOperator operator, Object value) {
    public ParsedFilter {
      Objects.requireNonNull(field, "field must not be null");
      Objects.requireNonNull(operator, "operator must not be null");
    }
  }

  public record ParsedOrGroup(List<ParsedFilter> filters) {
    public ParsedOrGroup {
      Objects.requireNonNull(filters, "filters must not be null");
      filters = List.copyOf(filters);
    }
  }
}
