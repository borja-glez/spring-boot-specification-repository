package com.borjaglez.specrepository.http;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.core.QueryPlanBuilder;
import com.borjaglez.specrepository.core.SpecificationQueryBuilder;
import com.borjaglez.specrepository.http.ParsedHttpQuery.ParsedFilter;
import com.borjaglez.specrepository.http.ParsedHttpQuery.ParsedOrGroup;

public final class HttpFilterParser {

  private static final Pattern SAFE_FIELD_NAME =
      Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*");

  private static final Set<String> VALUELESS_OPERATORS =
      Set.of("isnull", "isnotnull", "isempty", "isnotempty");

  private static final Set<String> MULTI_VALUE_OPERATORS = Set.of("in", "notin", "between");

  private static final Set<FilterOperator> CASE_INSENSITIVE_OPERATORS =
      Set.of(
          Operators.EQUALS,
          Operators.NOT_EQUALS,
          Operators.CONTAINS,
          Operators.NOT_CONTAINS,
          Operators.STARTS_WITH,
          Operators.ENDS_WITH);

  private final HttpFilterParserConfiguration config;

  public HttpFilterParser() {
    this(HttpFilterParserConfiguration.defaults());
  }

  public HttpFilterParser(HttpFilterParserConfiguration config) {
    this.config = Objects.requireNonNull(config, "config must not be null");
  }

  public ParsedHttpQuery parse(Map<String, List<String>> params) {
    Objects.requireNonNull(params, "params must not be null");
    List<ParsedFilter> filters = parseFilters(params);
    List<ParsedOrGroup> orGroups = parseOrGroups(params);
    Sort sort = parseSort(params);
    return new ParsedHttpQuery(filters, orGroups, sort);
  }

  public <T> QueryPlanBuilder<T> applyTo(
      QueryPlanBuilder<T> builder, Map<String, List<String>> params) {
    Objects.requireNonNull(builder, "builder must not be null");
    ParsedHttpQuery parsed = parse(params);
    return applyParsed(builder, parsed, Set.of());
  }

  public <T> QueryPlan<T> toQueryPlan(Class<T> entityType, Map<String, List<String>> params) {
    Objects.requireNonNull(entityType, "entityType must not be null");
    ParsedHttpQuery parsed = parse(params);
    QueryPlanBuilder<T> builder = SpecificationQueryBuilder.forEntity(entityType);
    return applyParsed(builder, parsed, Set.of()).build();
  }

  public <T> QueryPlan<T> toQueryPlan(
      Class<T> entityType, Map<String, List<String>> params, AllowedFieldsPolicy policy) {
    return toQueryPlan(entityType, params, policy, Set.of());
  }

  /**
   * Builds a query plan like {@link #toQueryPlan(Class, Map, AllowedFieldsPolicy)} and matches the
   * given fields case-insensitively.
   *
   * <p>Conditions on a field in {@code caseInsensitiveFields}, in {@code filter} as well as in
   * {@code orFilter}, get {@code ignoreCase = true} when their operator is {@code eq}, {@code neq},
   * {@code contains}, {@code notcontains}, {@code startswith} or {@code endswith}. Other operators
   * on those fields keep the default matching. With the default operator handlers on PostgreSQL the
   * comparison is also accent-insensitive ({@code unaccent}).
   *
   * @param caseInsensitiveFields fields matched case-insensitively; empty for none
   */
  public <T> QueryPlan<T> toQueryPlan(
      Class<T> entityType,
      Map<String, List<String>> params,
      AllowedFieldsPolicy policy,
      Set<String> caseInsensitiveFields) {
    Objects.requireNonNull(entityType, "entityType must not be null");
    Objects.requireNonNull(policy, "policy must not be null");
    Objects.requireNonNull(caseInsensitiveFields, "caseInsensitiveFields must not be null");
    ParsedHttpQuery parsed = parse(params);
    QueryPlanBuilder<T> builder = SpecificationQueryBuilder.forEntity(entityType);
    return applyParsed(builder, parsed, caseInsensitiveFields).allowedFields(policy).build();
  }

  private <T> QueryPlanBuilder<T> applyParsed(
      QueryPlanBuilder<T> builder, ParsedHttpQuery parsed, Set<String> caseInsensitiveFields) {
    for (ParsedFilter filter : parsed.filters()) {
      builder.where(
          filter.field(),
          filter.operator(),
          filter.value(),
          ignoreCase(filter, caseInsensitiveFields),
          false);
    }
    for (ParsedOrGroup orGroup : parsed.orGroups()) {
      builder.or(
          g -> {
            for (ParsedFilter filter : orGroup.filters()) {
              g.where(
                  filter.field(),
                  filter.operator(),
                  filter.value(),
                  ignoreCase(filter, caseInsensitiveFields),
                  false);
            }
          });
    }
    if (parsed.sort().isSorted()) {
      builder.sort(parsed.sort());
    }
    return builder;
  }

  private static boolean ignoreCase(ParsedFilter filter, Set<String> caseInsensitiveFields) {
    return caseInsensitiveFields.contains(filter.field())
        && CASE_INSENSITIVE_OPERATORS.contains(filter.operator());
  }

  private List<ParsedFilter> parseFilters(Map<String, List<String>> params) {
    List<String> values = params.getOrDefault(config.filterParam(), Collections.emptyList());
    validateFilterCount(values.size());
    List<ParsedFilter> filters = new ArrayList<>();
    for (String raw : values) {
      filters.add(parseFilterExpression(raw));
    }
    return filters;
  }

  private List<ParsedOrGroup> parseOrGroups(Map<String, List<String>> params) {
    List<String> values = params.getOrDefault(config.orFilterParam(), Collections.emptyList());
    List<ParsedOrGroup> groups = new ArrayList<>();
    int totalFilterCount =
        params.getOrDefault(config.filterParam(), Collections.emptyList()).size();
    for (String raw : values) {
      String[] parts = raw.split(Pattern.quote(config.orGroupSeparator()), -1);
      totalFilterCount += parts.length;
      validateFilterCount(totalFilterCount);
      List<ParsedFilter> filters = new ArrayList<>();
      for (String part : parts) {
        filters.add(parseFilterExpression(part));
      }
      groups.add(new ParsedOrGroup(filters));
    }
    return groups;
  }

  private ParsedFilter parseFilterExpression(String raw) {
    if (raw.isEmpty()) {
      throw new HttpFilterSyntaxException(raw, "expression must not be empty");
    }
    int firstColon = raw.indexOf(':');
    if (firstColon < 1) {
      throw new HttpFilterSyntaxException(raw, "missing field name or operator separator");
    }
    String field = raw.substring(0, firstColon);
    validateFieldName(raw, field);
    String rest = raw.substring(firstColon + 1);

    String operator;
    String rawValue;
    int secondColon = rest.indexOf(':');
    if (secondColon < 0) {
      operator = rest;
      rawValue = null;
    } else {
      operator = rest.substring(0, secondColon);
      rawValue = rest.substring(secondColon + 1);
    }

    if (operator.isEmpty()) {
      throw new HttpFilterSyntaxException(raw, "operator must not be empty");
    }
    String normalized = operator.toLowerCase(Locale.ROOT);
    validateOperator(operator, normalized);

    Object value = resolveValue(raw, operator, normalized, rawValue);
    return new ParsedFilter(field, FilterOperator.of(normalized), value);
  }

  private Object resolveValue(String raw, String operator, String lowerOp, String rawValue) {
    if (VALUELESS_OPERATORS.contains(lowerOp)) {
      return null;
    }
    if (rawValue == null || rawValue.isEmpty()) {
      throw new HttpFilterSyntaxException(raw, "value is required for operator '" + operator + "'");
    }
    if (MULTI_VALUE_OPERATORS.contains(lowerOp)) {
      List<String> values =
          Arrays.asList(rawValue.split(Pattern.quote(config.multiValueSeparator()), -1));
      if ("between".equals(lowerOp)
          && (values.size() != 2 || values.get(0).isEmpty() || values.get(1).isEmpty())) {
        throw new HttpFilterSyntaxException(
            raw, "operator '" + operator + "' requires exactly 2 non-empty values");
      }
      return values;
    }
    return rawValue;
  }

  private Sort parseSort(Map<String, List<String>> params) {
    List<String> values = params.getOrDefault(config.sortParam(), Collections.emptyList());
    if (values.isEmpty()) {
      return Sort.unsorted();
    }
    if (values.size() > config.maxSortFields()) {
      throw new HttpFilterSyntaxException(
          String.join(", ", values), "too many sort fields (max " + config.maxSortFields() + ")");
    }
    List<Sort.Order> orders = new ArrayList<>();
    for (String raw : values) {
      orders.add(parseSortExpression(raw));
    }
    return Sort.by(orders);
  }

  private Sort.Order parseSortExpression(String raw) {
    if (raw.isEmpty()) {
      throw new HttpFilterSyntaxException(raw, "sort expression must not be empty");
    }
    String[] parts = raw.split(",", -1);
    if (parts.length > 2) {
      throw new HttpFilterSyntaxException(
          raw, "sort expression must have at most 2 parts (field,direction)");
    }
    String field = parts[0].trim();
    if (field.isEmpty()) {
      throw new HttpFilterSyntaxException(raw, "sort field must not be empty");
    }
    validateFieldName(raw, field);
    if (parts.length == 1) {
      return Sort.Order.asc(field);
    }
    String direction = parts[1].trim().toLowerCase(Locale.ROOT);
    return switch (direction) {
      case "asc" -> Sort.Order.asc(field);
      case "desc" -> Sort.Order.desc(field);
      default ->
          throw new HttpFilterSyntaxException(
              raw, "invalid sort direction '" + parts[1].trim() + "' (expected 'asc' or 'desc')");
    };
  }

  private void validateOperator(String operator, String normalized) {
    Set<String> allowed = config.allowedOperators();
    if (allowed != null && !allowed.contains(normalized)) {
      throw new HttpUnknownOperatorException(operator);
    }
  }

  private void validateFieldName(String raw, String field) {
    if (!SAFE_FIELD_NAME.matcher(field).matches()) {
      throw new HttpFilterSyntaxException(raw, "invalid field name '" + field + "'");
    }
  }

  private void validateFilterCount(int count) {
    if (count > config.maxFilters()) {
      throw new HttpFilterSyntaxException(
          count + " filters", "too many filters (max " + config.maxFilters() + ")");
    }
  }
}
