package com.borjaglez.specrepository.http;

import java.util.Objects;
import java.util.Set;

public final class HttpFilterParserConfiguration {
  private final String filterParam;
  private final String orFilterParam;
  private final String sortParam;
  private final String multiValueSeparator;
  private final String orGroupSeparator;
  private final int maxFilters;
  private final int maxSortFields;
  private final Set<String> allowedOperators;

  private HttpFilterParserConfiguration(Builder builder) {
    this.filterParam = builder.filterParam;
    this.orFilterParam = builder.orFilterParam;
    this.sortParam = builder.sortParam;
    this.multiValueSeparator = builder.multiValueSeparator;
    this.orGroupSeparator = builder.orGroupSeparator;
    this.maxFilters = builder.maxFilters;
    this.maxSortFields = builder.maxSortFields;
    this.allowedOperators =
        builder.allowedOperators == null ? null : Set.copyOf(builder.allowedOperators);
  }

  public static HttpFilterParserConfiguration defaults() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public String filterParam() {
    return filterParam;
  }

  public String orFilterParam() {
    return orFilterParam;
  }

  public String sortParam() {
    return sortParam;
  }

  public String multiValueSeparator() {
    return multiValueSeparator;
  }

  public String orGroupSeparator() {
    return orGroupSeparator;
  }

  public int maxFilters() {
    return maxFilters;
  }

  public int maxSortFields() {
    return maxSortFields;
  }

  public Set<String> allowedOperators() {
    return allowedOperators;
  }

  public static final class Builder {
    private String filterParam = "filter";
    private String orFilterParam = "orFilter";
    private String sortParam = "sort";
    private String multiValueSeparator = "|";
    private String orGroupSeparator = ";";
    private int maxFilters = 20;
    private int maxSortFields = 5;
    private Set<String> allowedOperators;

    private Builder() {}

    public Builder filterParam(String filterParam) {
      this.filterParam = Objects.requireNonNull(filterParam, "filterParam must not be null");
      return this;
    }

    public Builder orFilterParam(String orFilterParam) {
      this.orFilterParam = Objects.requireNonNull(orFilterParam, "orFilterParam must not be null");
      return this;
    }

    public Builder sortParam(String sortParam) {
      this.sortParam = Objects.requireNonNull(sortParam, "sortParam must not be null");
      return this;
    }

    public Builder multiValueSeparator(String multiValueSeparator) {
      this.multiValueSeparator =
          Objects.requireNonNull(multiValueSeparator, "multiValueSeparator must not be null");
      return this;
    }

    public Builder orGroupSeparator(String orGroupSeparator) {
      this.orGroupSeparator =
          Objects.requireNonNull(orGroupSeparator, "orGroupSeparator must not be null");
      return this;
    }

    public Builder maxFilters(int maxFilters) {
      if (maxFilters < 1) {
        throw new IllegalArgumentException("maxFilters must be at least 1");
      }
      this.maxFilters = maxFilters;
      return this;
    }

    public Builder maxSortFields(int maxSortFields) {
      if (maxSortFields < 1) {
        throw new IllegalArgumentException("maxSortFields must be at least 1");
      }
      this.maxSortFields = maxSortFields;
      return this;
    }

    public Builder allowedOperators(Set<String> allowedOperators) {
      this.allowedOperators = allowedOperators;
      return this;
    }

    public HttpFilterParserConfiguration build() {
      return new HttpFilterParserConfiguration(this);
    }
  }
}
