package com.borjaglez.specrepository.core;

import java.util.Objects;
import java.util.Set;

public final class AllowedFieldsPolicy {
  private static final AllowedFieldsPolicy ALLOW_ALL = new AllowedFieldsPolicy(null, null);

  private final Set<String> filterableFields;
  private final Set<String> sortableFields;

  private AllowedFieldsPolicy(Set<String> filterableFields, Set<String> sortableFields) {
    this.filterableFields = filterableFields;
    this.sortableFields = sortableFields;
  }

  public static AllowedFieldsPolicy allowAll() {
    return ALLOW_ALL;
  }

  public static AllowedFieldsPolicy of(Set<String> filterable, Set<String> sortable) {
    Objects.requireNonNull(filterable, "filterable must not be null");
    Objects.requireNonNull(sortable, "sortable must not be null");
    return new AllowedFieldsPolicy(Set.copyOf(filterable), Set.copyOf(sortable));
  }

  public void validateFilter(String field) {
    if (filterableFields != null && !filterableFields.contains(field)) {
      throw new DisallowedFieldException(field, "filtering");
    }
  }

  public void validateSort(String field) {
    if (sortableFields != null && !sortableFields.contains(field)) {
      throw new DisallowedFieldException(field, "sorting");
    }
  }

  public boolean isAllowAll() {
    return this == ALLOW_ALL;
  }
}
