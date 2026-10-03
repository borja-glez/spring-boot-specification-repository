package com.borjaglez.specrepository.http.spring;

import static org.apiguardian.api.API.Status.MAINTAINED;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apiguardian.api.API;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.borjaglez.specrepository.http.HttpFilterParser;
import com.borjaglez.specrepository.http.HttpFilterParserConfiguration;

/**
 * Configuration properties of the auto-configured {@link HttpFilterParser}, bound from {@code
 * specrepository.http}. The defaults match {@link HttpFilterParserConfiguration#defaults()}, and
 * the values are validated by {@link HttpFilterParserConfiguration.Builder}. A user-defined {@link
 * HttpFilterParser} bean replaces the auto-configured parser, and these properties are then
 * ignored.
 */
@ConfigurationProperties("specrepository.http")
@API(status = MAINTAINED, since = "1.0.0")
public class HttpFilterProperties {

  /** Name of the query parameter that carries the AND-combined filters. */
  private String filterParam = "filter";

  /** Name of the query parameter that carries an OR group of filters. */
  private String orFilterParam = "orFilter";

  /** Name of the query parameter that carries the sort fields. */
  private String sortParam = "sort";

  /** Separator between the values of a multi-value operator (in, notin, between). */
  private String multiValueSeparator = "|";

  /** Separator between the filters of an OR group. */
  private String orGroupSeparator = ";";

  /** Maximum number of filters in a request, counting every OR group. Must be at least 1. */
  private int maxFilters = 20;

  /** Maximum number of sort fields in a request. Must be at least 1. */
  private int maxSortFields = 5;

  /** Maximum number of values of an in or notin filter. Must be at least 1. */
  private int maxValuesPerFilter = 100;

  /** Maximum length, in characters, of a single filter value. Must be at least 1. */
  private int maxValueLength = 1000;

  /** Operators a client may use, case-insensitive. Empty allows every registered operator. */
  private Set<String> allowedOperators = new LinkedHashSet<>();

  public String getFilterParam() {
    return filterParam;
  }

  public void setFilterParam(String filterParam) {
    this.filterParam = filterParam;
  }

  public String getOrFilterParam() {
    return orFilterParam;
  }

  public void setOrFilterParam(String orFilterParam) {
    this.orFilterParam = orFilterParam;
  }

  public String getSortParam() {
    return sortParam;
  }

  public void setSortParam(String sortParam) {
    this.sortParam = sortParam;
  }

  public String getMultiValueSeparator() {
    return multiValueSeparator;
  }

  public void setMultiValueSeparator(String multiValueSeparator) {
    this.multiValueSeparator = multiValueSeparator;
  }

  public String getOrGroupSeparator() {
    return orGroupSeparator;
  }

  public void setOrGroupSeparator(String orGroupSeparator) {
    this.orGroupSeparator = orGroupSeparator;
  }

  public int getMaxFilters() {
    return maxFilters;
  }

  public void setMaxFilters(int maxFilters) {
    this.maxFilters = maxFilters;
  }

  public int getMaxSortFields() {
    return maxSortFields;
  }

  public void setMaxSortFields(int maxSortFields) {
    this.maxSortFields = maxSortFields;
  }

  public int getMaxValuesPerFilter() {
    return maxValuesPerFilter;
  }

  public void setMaxValuesPerFilter(int maxValuesPerFilter) {
    this.maxValuesPerFilter = maxValuesPerFilter;
  }

  public int getMaxValueLength() {
    return maxValueLength;
  }

  public void setMaxValueLength(int maxValueLength) {
    this.maxValueLength = maxValueLength;
  }

  public Set<String> getAllowedOperators() {
    return allowedOperators;
  }

  public void setAllowedOperators(Set<String> allowedOperators) {
    this.allowedOperators = allowedOperators;
  }

  /**
   * Builds the parser configuration through {@link HttpFilterParserConfiguration#builder()}, so the
   * builder validation applies. An empty {@link #getAllowedOperators() allowedOperators} leaves
   * every operator allowed.
   *
   * @return the parser configuration described by these properties
   * @throws NullPointerException if a parameter name or separator is {@code null}
   * @throws IllegalArgumentException if a limit is lower than 1
   */
  public HttpFilterParserConfiguration toParserConfiguration() {
    HttpFilterParserConfiguration.Builder builder =
        HttpFilterParserConfiguration.builder()
            .filterParam(filterParam)
            .orFilterParam(orFilterParam)
            .sortParam(sortParam)
            .multiValueSeparator(multiValueSeparator)
            .orGroupSeparator(orGroupSeparator)
            .maxFilters(maxFilters)
            .maxSortFields(maxSortFields)
            .maxValuesPerFilter(maxValuesPerFilter)
            .maxValueLength(maxValueLength);
    if (!allowedOperators.isEmpty()) {
      builder.allowedOperators(Set.copyOf(allowedOperators));
    }
    return builder.build();
  }
}
