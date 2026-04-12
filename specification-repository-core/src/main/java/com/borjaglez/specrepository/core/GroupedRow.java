package com.borjaglez.specrepository.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class GroupedRow {
  private final List<String> columns;
  private final Object[] values;

  public GroupedRow(List<String> columns, Object[] values) {
    Objects.requireNonNull(columns, "columns must not be null");
    Objects.requireNonNull(values, "values must not be null");
    if (columns.size() != values.length) {
      throw new IllegalArgumentException(
          "columns and values must have the same size: " + columns.size() + " != " + values.length);
    }
    this.columns = List.copyOf(columns);
    this.values = values.clone();
  }

  public List<String> columns() {
    return columns;
  }

  public Object[] values() {
    return values.clone();
  }

  public Object get(int index) {
    if (index < 0 || index >= values.length) {
      throw new IndexOutOfBoundsException("index out of bounds: " + index);
    }
    return values[index];
  }

  public Object get(String column) {
    Objects.requireNonNull(column, "column must not be null");
    int index = columns.indexOf(column);
    if (index < 0) {
      throw new IllegalArgumentException("unknown column: " + column);
    }
    return values[index];
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof GroupedRow row)) {
      return false;
    }
    return columns.equals(row.columns) && Arrays.equals(values, row.values);
  }

  @Override
  public int hashCode() {
    return 31 * columns.hashCode() + Arrays.hashCode(values);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("GroupedRow{");
    for (int i = 0; i < columns.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(columns.get(i)).append('=').append(values[i]);
    }
    return sb.append('}').toString();
  }
}
